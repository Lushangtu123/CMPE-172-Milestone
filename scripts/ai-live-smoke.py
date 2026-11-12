#!/usr/bin/env python3
"""Run after enabling Gemini. Writes require an explicit flag and use demo accounts."""
import argparse
import http.cookiejar
import json
import re
import urllib.error
import urllib.parse
import urllib.request

class Browser:
    def __init__(self, base):
        self.base = base.rstrip('/')
        self.csrf = ''
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
    def request(self, path, fields=None):
        data = None if fields is None else urllib.parse.urlencode(fields).encode()
        req = urllib.request.Request(self.base + path, data=data)
        if data is not None:
            req.add_header('X-CSRF-TOKEN', self.csrf)
        try:
            with self.opener.open(req, timeout=180) as response:
                body = response.read().decode()
        except urllib.error.HTTPError as error:
            raise SystemExit(f'HTTP {error.code} from {path}: {error.read().decode()}') from None
        return json.loads(body) if body.lstrip().startswith(('{', '[')) else body
    def token(self, path):
        page = self.request(path)
        match = re.search(r'name="_csrf"[^>]*value="([^"]+)"', page)
        if not match:
            raise SystemExit('Could not obtain the login/session CSRF token.')
        self.csrf = match.group(1)
    def login(self, email):
        self.token('/login')
        self.request('/login', {'username': email, 'password': 'TutorDemo123!'})
        self.token('/slots')
    def chat(self, message):
        return self.request('/api/customer/ai/chat', {'message': message})

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--base-url', default='http://localhost:8080')
parser.add_argument('--confirm-writes', action='store_true', help='Book and then cancel one demo slot; manually trigger reminders for its provider.')
args = parser.parse_args()
b = Browser(args.base_url)
b.login('maya.patel@example.com')
answer = b.chat('What does TutorConnect say about cancellation? Cite the retrieved policy ID.')
assert answer['messages'][-1]['text'].strip()
print('LIVE Gemini policy response:', answer['messages'][-1]['text'])
if not args.confirm_writes:
    print('Read-only smoke complete. No booking/cancellation was confirmed.')
    raise SystemExit(0)
slots = b.request('/api/slots?size=100')['items']
if not slots:
    raise SystemExit('No future slots available. Add one through the provider dashboard first.')
slot = slots[0]
proposal = b.chat(f'Please propose booking slot {slot["slotId"]} for service {slot["serviceId"]}. Use the book tool. Do not substitute another slot.')
pending = proposal.get('pending')
if not pending or pending['action'] != 'BOOK' or pending['targetId'] != slot['slotId']:
    raise SystemExit('The model did not propose the requested slot. Review the assistant in the browser.')
print('Confirming demo proposal:', pending['summary'])
result = b.request('/api/customer/ai/confirm', {'token': pending['token']})
appointment = result['appointmentId']
print('LIVE transactional booking confirmed:', appointment)
try:
    provider = Browser(args.base_url)
    provider.login('priya.shah@example.com' if slot['providerId'] == 1 else 'jordan.lee@example.com')
    print('LIVE manual reminder run:', provider.request('/api/provider/ai/reminders/run', {}))
finally:
    # Use the original service-backed API for reliable cleanup even if the LLM is unavailable.
    b.request(f'/api/customer/appointments/{appointment}/cancel', {})
    print('Demo booking cancelled through the existing service API:', appointment)
print('Use the assistant UI to record a separate AI cancellation proposal and explicit confirmation.')
