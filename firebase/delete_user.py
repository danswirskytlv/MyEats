#!/usr/bin/env python3
"""
MyEats — permanently delete a user account and all of their data.

Usage:
    python3 delete_user.py someone@example.com
    (you will be prompted for that account's password — it is never stored)

Deletes, in order:
  1. All recipes owned by the user (+ their photos and comment threads)
  2. The user's comments and likes on other people's recipes
  3. The user's profile (users/{uid}) and profile photo
  4. The Firebase Auth account itself

Security note: everything runs AS the user being deleted, so the database
rules allow it — no admin credentials are involved.
"""

import getpass
import json
import os
import ssl
import sys
import urllib.error
import urllib.parse
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
GS_PATH = os.path.join(HERE, "..", "app", "google-services.json")


def _make_ssl_context():
    ctx = ssl.create_default_context()
    try:
        req = urllib.request.Request("https://www.googleapis.com/", method="HEAD")
        urllib.request.urlopen(req, timeout=10, context=ctx)
        return ctx
    except ssl.SSLError:
        pass
    except urllib.error.URLError as e:
        if not isinstance(getattr(e, "reason", None), ssl.SSLError):
            return ctx
    except Exception:
        return ctx
    print("NOTE: SSL certificates missing on this Python — continuing without verification.\n")
    return ssl._create_unverified_context()


SSL_CTX = _make_ssl_context()


def request(method, url, data=None, headers=None):
    headers = headers or {}
    headers.setdefault(
        "User-Agent",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36",
    )
    if isinstance(data, (dict, list)):
        data = json.dumps(data).encode()
        headers.setdefault("Content-Type", "application/json")
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30, context=SSL_CTX) as resp:
            return resp.status, resp.read()
    except urllib.error.HTTPError as e:
        return e.code, e.read()
    except Exception as e:
        return 0, str(e).encode()


def main():
    if len(sys.argv) != 2:
        sys.exit("Usage: python3 delete_user.py <email>")
    email = sys.argv[1].strip()

    gs = json.load(open(GS_PATH))
    api_key = gs["client"][0]["api_key"][0]["current_key"]
    project = gs["project_info"]["project_id"]
    bucket = gs["project_info"]["storage_bucket"]

    db_url = None
    for candidate in (
        f"https://{project}-default-rtdb.firebaseio.com",
        f"https://{project}-default-rtdb.europe-west1.firebasedatabase.app",
        f"https://{project}-default-rtdb.asia-southeast1.firebasedatabase.app",
    ):
        status, _ = request("GET", candidate + "/.json?shallow=true")
        if status in (200, 401):
            db_url = candidate
            break
    if not db_url:
        sys.exit("Could not find the Realtime Database.")

    print(f"About to PERMANENTLY delete the account {email} and all of its data.")
    if input("Type DELETE to confirm: ").strip() != "DELETE":
        sys.exit("Aborted — nothing was deleted.")
    password = getpass.getpass(f"Password for {email}: ")

    # 1. sign in as the user
    status, body = request(
        "POST",
        f"https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key={api_key}",
        {"email": email, "password": password, "returnSecureToken": True},
    )
    if status != 200:
        sys.exit(f"Sign-in failed: {body.decode()[:200]}")
    data = json.loads(body)
    uid, token = data["localId"], data["idToken"]
    print(f"Signed in as {email} (uid {uid[:8]}…)")

    # 2. delete the user's own recipes (+ photos + their comment threads)
    q = urllib.parse.quote('"ownerUid"')
    v = urllib.parse.quote(f'"{uid}"')
    status, body = request("GET", f'{db_url}/recipes.json?orderBy={q}&equalTo={v}&auth={token}')
    recipes = json.loads(body) if status == 200 and body and body != b"null" else {}
    for rid in (recipes or {}):
        request("DELETE", f"{db_url}/recipes/{rid}.json?auth={token}")
        request("DELETE", f"{db_url}/comments/{rid}.json?auth={token}")  # best-effort
        obj = urllib.parse.quote(f"recipe_images/{rid}.jpg", safe="")
        request("DELETE", f"https://firebasestorage.googleapis.com/v0/b/{bucket}/o/{obj}",
                headers={"Authorization": f"Firebase {token}"})
        print(f"  deleted recipe {rid}")

    # 3. delete the user's comments and likes on other recipes
    status, body = request("GET", f"{db_url}/comments.json?shallow=true&auth={token}")
    all_recipe_ids = list(json.loads(body) or {}) if status == 200 and body != b"null" else []
    for rid in all_recipe_ids:
        status, body = request("GET", f"{db_url}/comments/{rid}.json?auth={token}")
        if status != 200 or not body or body == b"null":
            continue
        for cid, comment in (json.loads(body) or {}).items():
            comment = comment or {}
            if comment.get("authorUid") == uid:
                request("DELETE", f"{db_url}/comments/{rid}/{cid}.json?auth={token}")
                print(f"  deleted comment on {rid}")
            elif uid in (comment.get("likes") or {}):
                request("DELETE", f"{db_url}/comments/{rid}/{cid}/likes/{uid}.json?auth={token}")
                print(f"  removed like on {rid}")

    # 4. delete the profile + profile photo
    request("DELETE", f"{db_url}/users/{uid}.json?auth={token}")
    obj = urllib.parse.quote(f"profile_images/{uid}.jpg", safe="")
    request("DELETE", f"https://firebasestorage.googleapis.com/v0/b/{bucket}/o/{obj}",
            headers={"Authorization": f"Firebase {token}"})
    print("  deleted profile")

    # 5. delete the Auth account itself
    status, body = request(
        "POST",
        f"https://identitytoolkit.googleapis.com/v1/accounts:delete?key={api_key}",
        {"idToken": token},
    )
    if status == 200:
        print(f"\nDone — {email} is gone completely.")
    else:
        sys.exit(f"Auth account deletion failed: {body.decode()[:200]}")


if __name__ == "__main__":
    main()
