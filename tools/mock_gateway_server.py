#!/usr/bin/env python3
"""
Mock payment gateway server implementing the contract in docs/API.md, for exercising
the Android SDK's checkout flow end-to-end without a real gateway. Field shapes here are
cross-checked against the sibling iOS SDK's PGAPIModels.swift, the authoritative
reference for this contract.

Usage:
    python3 tools/mock_gateway_server.py [port]   # default port 8080

Point PGConfig.backendBaseUrl at it. From an Android emulator that's
http://10.0.2.2:<port>/ (the sample app is already configured this way, see
PGSampleApp.kt). From a physical device on the same network, use your machine's LAN IP.

No dependencies -- stdlib only.

=====================================================================================
 TEST INPUT CHEATSHEET -- what to type into the checkout UI to hit each outcome
=====================================================================================

CARD (any 12-19 digit number that passes Luhn is accepted by the client's own
validation; only the LAST 4 DIGITS below matter to this mock server; cardholder name is
required by the form but its value doesn't affect the outcome):

  Card number ending  Expiry (any future MM/YY)  CVV        Result
  ------------------  -------------------------  ---------  ------------------------
  4242 4242 4242 4242 (ends 4242)  12/30          123        Immediate success
  4111 1111 1111 0002 (ends 0002) 12/30           123        Tokenize call itself
                                                              fails with HTTP 402
                                                              "card_declined" -> tests
                                                              PGErrorFragment's
                                                              Retry/Cancel screen.
  4111 1111 1111 0001 (ends 0001) 12/30           123        Tokenize succeeds, but
                                                              the attempt later
                                                              resolves to "failed" ->
                                                              tests the terminal
                                                              Failure result screen.
  4111 1111 1111 1117 (ends 1117) 12/30           123        Tokenize returns a 3DS
                                                              redirect_url -> opens
                                                              the WebRedirect screen;
                                                              use the "Approve" /
                                                              "Decline" links on the
                                                              page it loads.
  Anything else that passes Luhn                  any        Succeeds after ~3s
                                                              (2 poll ticks).

  Non-Luhn number, e.g. 1234 5678 9012 3456          -> rejected client-side before
                                                         any network call ("Enter a
                                                         valid card number").
  Past expiry, e.g. 01/20                            -> rejected client-side.
  Blank cardholder name                              -> rejected client-side.

UPI (manual VPA entry -- app-intent launch needs a real UPI app installed, which an
emulator normally doesn't have, so use "Enter UPI ID" instead):

  VPA                 Result
  -------------------  ------------------------------------------------------------
  success@okhdfcbank   Succeeds after ~3s (2 poll ticks). Any VPA not listed below
                        behaves the same way.
  fail@okhdfcbank       Resolves to "failed" after ~3s.
  pending@okhdfcbank    Never resolves (stays "pending") -- exhausts the poller
                        after ~20 attempts (a few minutes) and surfaces
                        PGPaymentResult.Pending. Good for testing that path without
                        waiting the full time: restart the app once you've seen the
                        "Waiting for you to approve..." screen for a bit.
  not-a-vpa             Rejected client-side before any network call.

NET BANKING: pick any bank -> a WebView opens a local mock bank page with "Approve
payment" / "Decline payment" links. Tap one; the app's background poller (next tick,
within ~1.5-8s) picks up the result and closes the WebView automatically.
=====================================================================================
"""

import json
import re
import sys
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8080

# attempt_id -> {status, method, order_token, poll_count, polls_until_terminal,
#                outcome, failure_reason, verification_reference}
ATTEMPTS: dict[str, dict] = {}
IDEMPOTENCY: dict[str, str] = {}

BANKS = [
    {"id": "HDFC", "name": "HDFC Bank", "icon_url": None},
    {"id": "ICIC", "name": "ICICI Bank", "icon_url": None},
    {"id": "SBIN", "name": "State Bank of India", "icon_url": None},
]


def error_body(code: str, message: str) -> bytes:
    return json.dumps({"code": code, "message": message}).encode()


class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        print(f"[mock-gateway] {self.address_string()} {fmt % args}")

    def _send_json(self, status: int, payload) -> None:
        body = json.dumps(payload).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _send_error(self, status: int, code: str, message: str) -> None:
        body = error_body(code, message)
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _send_html(self, html: str) -> None:
        body = html.encode()
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _read_json(self):
        length = int(self.headers.get("Content-Length", "0") or "0")
        if length == 0:
            return {}
        return json.loads(self.rfile.read(length))

    def _attempt_created_public(self, attempt_id: str) -> dict:
        """Shape for POST /v1/payment_attempts -- narrower than the status shape."""
        a = ATTEMPTS[attempt_id]
        return {"attempt_id": attempt_id, "order_token": a["order_token"], "status": a["status"]}

    def _status_public(self, attempt_id: str) -> dict:
        """Shape for GET /v1/payment_attempts/{id}."""
        a = ATTEMPTS[attempt_id]
        return {
            "attempt_id": attempt_id,
            "order_token": a["order_token"],
            "status": a["status"],
            "method": a["method"],
            "verification_reference": a.get("verification_reference"),
            "failure_reason": a.get("failure_reason"),
        }

    def _advance(self, attempt_id: str) -> None:
        """Called on every GET status poll -- resolves pending/created attempts
        toward their scripted outcome."""
        a = ATTEMPTS[attempt_id]
        if a["status"] not in ("created", "pending"):
            return
        outcome = a.get("outcome")
        if outcome == "stay_pending":
            a["status"] = "pending"
            return
        a["poll_count"] += 1
        if a["poll_count"] < a.get("polls_until_terminal", 2):
            a["status"] = "pending"
            return
        if outcome == "fail":
            a["status"] = "failed"
            a["failure_reason"] = a.get("failure_reason", "The payment was declined.")
        else:
            a["status"] = "succeeded"
            a["verification_reference"] = f"mock_ref_{attempt_id}"

    # -- routing -----------------------------------------------------------------

    def do_GET(self):
        parsed = urlparse(self.path)
        path = parsed.path
        query = parse_qs(parsed.query)

        if path == "/v1/net_banking/banks":
            return self._send_json(200, {"banks": BANKS})

        m = re.fullmatch(r"/v1/payment_attempts/([^/]+)", path)
        if m:
            attempt_id = m.group(1)
            if attempt_id not in ATTEMPTS:
                return self._send_error(404, "not_found", "Unknown payment attempt.")
            self._advance(attempt_id)
            return self._send_json(200, self._status_public(attempt_id))

        if path == "/mock/bank-page":
            attempt_id = query.get("attempt_id", [""])[0]
            return self._send_html(self._bank_page_html(attempt_id))

        if path == "/mock/3ds-page":
            attempt_id = query.get("attempt_id", [""])[0]
            return self._send_html(self._three_ds_page_html(attempt_id))

        if path == "/mock/complete":
            attempt_id = query.get("attempt_id", [""])[0]
            outcome = query.get("outcome", ["succeeded"])[0]
            if attempt_id in ATTEMPTS:
                a = ATTEMPTS[attempt_id]
                if outcome == "failed":
                    a["status"] = "failed"
                    a["failure_reason"] = "You declined the payment on the bank page."
                else:
                    a["status"] = "succeeded"
                    a["verification_reference"] = f"mock_ref_{attempt_id}"
            return self._send_html(
                "<html><body style='font-family:sans-serif;padding:24px'>"
                "<h3>Done</h3><p>You can return to the app now.</p></body></html>"
            )

        return self._send_error(404, "not_found", "No such endpoint.")

    def do_POST(self):
        parsed = urlparse(self.path)
        path = parsed.path
        body = self._read_json()

        if path == "/v1/payment_attempts":
            return self._create_attempt(body)

        m = re.fullmatch(r"/v1/payment_attempts/([^/]+)/upi/intent", path)
        if m:
            return self._upi_intent(m.group(1))

        m = re.fullmatch(r"/v1/payment_attempts/([^/]+)/upi/collect", path)
        if m:
            return self._upi_collect(m.group(1), body)

        m = re.fullmatch(r"/v1/payment_attempts/([^/]+)/card/tokenize", path)
        if m:
            return self._card_tokenize(m.group(1), body)

        m = re.fullmatch(r"/v1/payment_attempts/([^/]+)/net_banking/initiate", path)
        if m:
            return self._net_banking_initiate(m.group(1), body)

        return self._send_error(404, "not_found", "No such endpoint.")

    # -- handlers ------------------------------------------------------------

    def _create_attempt(self, body):
        idempotency_key = self.headers.get("Idempotency-Key")
        if idempotency_key and idempotency_key in IDEMPOTENCY:
            return self._send_json(200, self._attempt_created_public(IDEMPOTENCY[idempotency_key]))

        attempt_id = "att_" + uuid.uuid4().hex[:12]
        ATTEMPTS[attempt_id] = {
            "status": "created",
            "method": body.get("method"),
            "order_token": body.get("order_token"),
            "poll_count": 0,
            "polls_until_terminal": 2,
            "outcome": "succeed",
        }
        if idempotency_key:
            IDEMPOTENCY[idempotency_key] = attempt_id
        return self._send_json(200, self._attempt_created_public(attempt_id))

    def _upi_intent(self, attempt_id):
        if attempt_id not in ATTEMPTS:
            return self._send_error(404, "not_found", "Unknown payment attempt.")
        intent_url = (
            f"upi://pay?pa=merchant@mockbank&pn=Mock%20Merchant"
            f"&am=1.00&cu=INR&tr={attempt_id}&tn=Test%20payment"
        )
        return self._send_json(200, {"intent_url": intent_url})

    def _upi_collect(self, attempt_id, body):
        if attempt_id not in ATTEMPTS:
            return self._send_error(404, "not_found", "Unknown payment attempt.")
        vpa = body.get("vpa", "")
        a = ATTEMPTS[attempt_id]
        if vpa.startswith("fail@"):
            a["outcome"] = "fail"
        elif vpa.startswith("pending@"):
            a["outcome"] = "stay_pending"
        else:
            a["outcome"] = "succeed"
        a["status"] = "pending"
        return self._send_json(200, {"status": a["status"]})

    def _card_tokenize(self, attempt_id, body):
        if attempt_id not in ATTEMPTS:
            return self._send_error(404, "not_found", "Unknown payment attempt.")
        number = body.get("card_number", "")
        a = ATTEMPTS[attempt_id]

        if number.endswith("0002"):
            return self._send_error(402, "card_declined", "The card was declined by the issuer.")

        if number.endswith("0001"):
            a["outcome"] = "fail"
            a["status"] = "pending"
            return self._send_json(
                200, {"card_token": "tok_mock_card", "three_ds_redirect_url": None, "status": a["status"]}
            )

        if number.endswith("1117"):
            a["outcome"] = "succeed"
            redirect_url = f"http://10.0.2.2:{PORT}/mock/3ds-page?attempt_id={attempt_id}"
            return self._send_json(
                200, {"card_token": "tok_mock_card", "three_ds_redirect_url": redirect_url, "status": a["status"]}
            )

        a["outcome"] = "succeed"
        a["status"] = "pending"
        return self._send_json(
            200, {"card_token": "tok_mock_card", "three_ds_redirect_url": None, "status": a["status"]}
        )

    def _net_banking_initiate(self, attempt_id, _body):
        # bank_id is intentionally ignored: the mock bank page below lets you pick
        # the outcome interactively regardless of which bank was chosen.
        if attempt_id not in ATTEMPTS:
            return self._send_error(404, "not_found", "Unknown payment attempt.")
        ATTEMPTS[attempt_id]["outcome"] = "succeed"
        redirect_url = f"http://10.0.2.2:{PORT}/mock/bank-page?attempt_id={attempt_id}"
        return self._send_json(200, {"redirect_url": redirect_url})

    # -- mock HTML pages -------------------------------------------------------

    def _bank_page_html(self, attempt_id: str) -> str:
        return f"""
        <html><body style='font-family:sans-serif;padding:24px'>
        <h3>Mock Bank Auth Page</h3>
        <p>Attempt: {attempt_id}</p>
        <p><a href="/mock/complete?attempt_id={attempt_id}&outcome=succeeded">Approve payment</a></p>
        <p><a href="/mock/complete?attempt_id={attempt_id}&outcome=failed">Decline payment</a></p>
        </body></html>
        """

    def _three_ds_page_html(self, attempt_id: str) -> str:
        return f"""
        <html><body style='font-family:sans-serif;padding:24px'>
        <h3>Mock 3DS Verification</h3>
        <p>Attempt: {attempt_id}</p>
        <p><a href="/mock/complete?attempt_id={attempt_id}&outcome=succeeded">Complete 3DS verification</a></p>
        </body></html>
        """


if __name__ == "__main__":
    server = ThreadingHTTPServer(("0.0.0.0", PORT), Handler)
    print(f"Mock gateway listening on http://0.0.0.0:{PORT} (emulator: http://10.0.2.2:{PORT}/)")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
