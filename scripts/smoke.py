#!/usr/bin/env python3
"""Verify a fresh demo deployment using only Python's standard library."""
import argparse, http.cookiejar, json, re, urllib.parse, urllib.request

class Browser:
    def __init__(self, base):
        self.base=base.rstrip('/')
        self.opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        self.csrf=None
    def get(self,path):
        with self.opener.open(self.base+path,timeout=10) as r:return r.read().decode()
    def post(self,path,fields):
        req=urllib.request.Request(self.base+path,data=urllib.parse.urlencode(fields).encode(),headers={'X-CSRF-TOKEN':self.csrf or ''})
        with self.opener.open(req,timeout=10) as r:return r.status,r.read().decode()
    def token(self,path):
        html=self.get(path)
        self.csrf=re.search(r'name="_csrf"[^>]*value="([^"]+)"',html).group(1)
    def login(self,email):
        self.token('/login');self.post('/login',{'username':email,'password':'TutorDemo123!'});self.token('/slots')

p=argparse.ArgumentParser();p.add_argument('--base-url',default='http://localhost:8080');args=p.parse_args()
b=Browser(args.base_url);health=json.loads(b.get('/health'));assert health=={'status':'UP','database':'UP'},health
b.login('maya.patel@example.com');slots=json.loads(b.get('/api/slots'))['items'];assert slots,'Fresh demo slots are required'
slot=slots[0];status,body=b.post('/api/customer/appointments',{'slotId':slot['slotId'],'serviceId':slot['serviceId']});assert status==201,(status,body)
id=json.loads(body)['appointmentId'];status,body=b.post(f'/api/customer/appointments/{id}/cancel',{});assert status==200,(status,body)
provider=Browser(args.base_url);provider.login('priya.shah@example.com');metrics=json.loads(provider.get('/api/provider/metrics'))
assert metrics['notificationsDelivered']>=2,metrics
print(json.dumps({'health':health,'bookingStatus':201,'cancellationStatus':200,'metrics':metrics},indent=2))
