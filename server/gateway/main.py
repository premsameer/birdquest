import os
import firebase_admin
from firebase_admin import auth
from fastapi import FastAPI, Request, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from starlette.concurrency import run_in_threadpool
import google.auth.transport.requests
import google.oauth2.id_token
import requests

PROJECT = os.environ['GOOGLE_CLOUD_PROJECT']
UPSTREAM = os.environ['RECOGNITION_URL'].rstrip('/')
firebase_admin.initialize_app(options={'projectId': PROJECT})
app = FastAPI()
app.add_middleware(CORSMiddleware, allow_origins=['https://appassets.androidplatform.net'], allow_methods=['POST'], allow_headers=['Authorization','Content-Type','X-Pairing-Key'])
LIMIT = 6 * 1024 * 1024

@app.get('/health')
def health():
    return {'status':'ok','firebase_project':PROJECT}

@app.post('/v1/identify')
async def identify(request: Request):
    header = request.headers.get('authorization','')
    if not header.startswith('Bearer '):
        raise HTTPException(401,'Sign in first.')
    try:
        claims = await run_in_threadpool(auth.verify_id_token,header[7:])
    except Exception:
        raise HTTPException(401,'Sign-in expired. Sign in again.')
    if claims.get('firebase',{}).get('sign_in_provider') != 'google.com' or not claims.get('email_verified'):
        raise HTTPException(403,'A verified Google sign-in is required.')
    allowed = {e.strip().lower() for e in os.environ.get('ALLOWED_EMAILS','').split(',') if e.strip()}
    if not allowed or claims.get('email','').lower() not in allowed:
        raise HTTPException(403,'This account is not enabled for testing.')
    content_type = request.headers.get('content-type','')
    if not content_type.startswith('multipart/form-data;'):
        raise HTTPException(415,'An audio upload is required.')
    body = bytearray()
    async for chunk in request.stream():
        if len(body)+len(chunk)>LIMIT:
            raise HTTPException(413,'Recording is too large.')
        body.extend(chunk)
    def forward():
        token = google.oauth2.id_token.fetch_id_token(google.auth.transport.requests.Request(),UPSTREAM)
        return requests.post(UPSTREAM+'/v1/identify',data=bytes(body),headers={'Authorization':'Bearer '+token,'Content-Type':content_type},timeout=(10,100))
    try:
        result = await run_in_threadpool(forward)
    except Exception:
        raise HTTPException(503,'Recognition is unavailable. Retry shortly.')
    if result.status_code != 200:
        raise HTTPException(result.status_code if result.status_code in (413,415,422,429) else 503,'Recording could not be identified. Try again.')
    try:
        return result.json()
    except ValueError:
        raise HTTPException(502,'Recognition returned an invalid response.')

# Ephemeral pairing: secrets are random, expire in five minutes, and are single-use.
import secrets
import time
from fastapi.responses import FileResponse
from threading import Lock
sessions = {}
session_lock = Lock()

@app.get('/login')
def login():
    return FileResponse('login.html',headers={'Cache-Control':'no-store','Referrer-Policy':'no-referrer'})

@app.post('/session')
def create_session():
    with session_lock:
        now=time.monotonic()
        for key in list(sessions):
            if sessions[key]['expires']<now: del sessions[key]
        if len(sessions)>=100: raise HTTPException(429,'Try again later.')
        key=secrets.token_urlsafe(32)
        sessions[key]={'expires':now+300,'token':None}
        return {'session':key}

def pairing_key(request):
    key=request.headers.get('X-Pairing-Key','')
    if len(key)<40: raise HTTPException(401,'Invalid pairing session.')
    return key

@app.post('/session/complete')
async def complete_session(request: Request):
    key=pairing_key(request)
    bearer=request.headers.get('Authorization','')
    if not bearer.startswith('Bearer '): raise HTTPException(401,'Sign in first.')
    token=bearer[7:]
    try: claims=await run_in_threadpool(auth.verify_id_token,token)
    except Exception: raise HTTPException(401,'Invalid sign-in.')
    allowed={e.strip().lower() for e in os.environ.get('ALLOWED_EMAILS','').split(',')}
    if not claims.get('email_verified') or claims.get('email','').lower() not in allowed or claims.get('firebase',{}).get('sign_in_provider')!='google.com':
        raise HTTPException(403,'Use your enabled test account.')
    with session_lock:
        entry=sessions.get(key)
        if not entry or entry['expires']<time.monotonic(): raise HTTPException(410,'Session expired. Restart sign-in in the app.')
        if entry['token']: raise HTTPException(409,'Already completed.')
        entry['token']=token
    return {'status':'complete'}

@app.post('/session/poll')
def poll_session(request: Request):
    key=pairing_key(request)
    with session_lock:
        entry=sessions.get(key)
        if not entry or entry['expires']<time.monotonic(): raise HTTPException(410,'Sign-in expired. Try again.')
        if not entry['token']: return {'status':'pending'}
        token=entry['token'];del sessions[key]
        from fastapi.responses import JSONResponse
        return JSONResponse({'status':'complete','token':token},headers={'Cache-Control':'no-store'})
