#!/usr/bin/env python3
"""Dry-run reconciliation report for PostgreSQL profiles missing from Keycloak.

This script never modifies PostgreSQL or Keycloak. It classifies each orphaned
profile and prints the recommended next action.
"""

import argparse
import json
import os
import subprocess
import sys
import urllib.parse
import urllib.request

KEYCLOAK_URL = os.getenv("KEYCLOAK_URL", "http://localhost:8081").rstrip("/")
REALM = os.getenv("KEYCLOAK_REALM", "pams")
CLIENT_ID = os.getenv("KEYCLOAK_ADMIN_CLIENT_ID", "pams-admin")
CLIENT_SECRET = os.getenv("KEYCLOAK_ADMIN_CLIENT_SECRET", "change-me-keycloak-admin-secret")
POSTGRES_USER = os.getenv("POSTGRES_USER", "pams")
POSTGRES_DB = os.getenv("POSTGRES_DB", "pams")


def keycloak_token() -> str:
    data = urllib.parse.urlencode(
        {
            "grant_type": "client_credentials",
            "client_id": CLIENT_ID,
            "client_secret": CLIENT_SECRET,
        }
    ).encode()
    request = urllib.request.Request(
        f"{KEYCLOAK_URL}/realms/{REALM}/protocol/openid-connect/token",
        data=data,
        headers={"Content-Type": "application/x-www-form-urlencoded"},
    )
    with urllib.request.urlopen(request) as response:
        return json.load(response)["access_token"]


def keycloak_users() -> list[dict]:
    bearer = keycloak_token()
    users: list[dict] = []
    page = 0
    while True:
        query = urllib.parse.urlencode({"first": page * 1000, "max": 1000})
        request = urllib.request.Request(
            f"{KEYCLOAK_URL}/admin/realms/{REALM}/users?{query}",
            headers={"Authorization": f"Bearer {bearer}"},
        )
        with urllib.request.urlopen(request) as response:
            batch = json.load(response)
        users.extend(batch)
        if len(batch) < 1000:
            return users
        page += 1


def profile_rows() -> list[dict]:
    query = """
        select json_agg(row_to_json(x))::text
          from (
            select up.keycloak_subject as subject,
                   up.email,
                   up.display_name as name,
                   o.name as organization,
                   d.name as department,
                   up.active,
                   (select count(*)::int from purchase_request pr
                     where pr.requester_subject = up.keycloak_subject) as requests,
                   (select count(*)::int from purchase_order po
                     where po.requester_subject = up.keycloak_subject) as purchase_orders,
                   (select count(*)::int from approval_instance ai
                     where ai.requester_subject = up.keycloak_subject) as approvals_created,
                   (select count(*)::int from approval_step st
                     where st.acted_by_subject = up.keycloak_subject) as approval_actions,
                   (select count(*)::int from audit_event ae
                     where ae.actor_subject = up.keycloak_subject) as audit_events,
                   (select count(*)::int from notification n
                     where n.recipient_subject = up.keycloak_subject) as notifications,
                   (select count(*)::int from user_profile child
                     where child.manager_id = up.id and child.deleted_at is null) as manager_reports,
                   (select count(*)::int from delegation del
                     where del.delegate_user_id = up.id or del.delegator_user_id = up.id) as delegations,
                   (select count(*)::int from catalog_favorite fav
                     where fav.user_profile_id = up.id) as catalog_favorites
              from user_profile up
              join organization o on o.id = up.organization_id
              left join department d on d.id = up.department_id
             where up.deleted_at is null
          ) x
    """
    result = subprocess.run(
        [
            "docker",
            "compose",
            "exec",
            "-T",
            "postgres",
            "psql",
            "-U",
            POSTGRES_USER,
            "-d",
            POSTGRES_DB,
            "-Atc",
            query,
        ],
        check=True,
        text=True,
        capture_output=True,
    )
    payload = result.stdout.strip()
    return json.loads(payload) if payload else []


def classify(profile: dict, keycloak_by_email: dict[str, list[dict]]) -> tuple[str, list[str]]:
    owned_data = (
        profile["requests"]
        + profile["purchase_orders"]
        + profile["notifications"]
        + profile["catalog_favorites"]
    )
    history = (
        profile["approvals_created"] + profile["approval_actions"] + profile["audit_events"]
    )
    references = profile["manager_reports"] + profile["delegations"]
    matches = keycloak_by_email.get((profile["email"] or "").lower(), [])
    duplicate_email = bool(matches)

    if owned_data > 0:
        action = "REINVITE_AND_RELINK"
    elif duplicate_email and references > 0:
        action = "REASSIGN_REFS_THEN_SOFT_DELETE"
    elif duplicate_email and history > 0:
        action = "REVIEW_DUPLICATE_HISTORY"
    elif duplicate_email:
        action = "SOFT_DELETE_DUPLICATE"
    elif references > 0:
        action = "REINVITE_OR_REASSIGN"
    elif history > 0:
        action = "REVIEW_HISTORICAL"
    else:
        action = "SOFT_DELETE"

    reasons: list[str] = []
    if owned_data:
        reasons.append("owns current requests/POs/notifications/favorites")
    if references:
        reasons.append("referenced by manager/delegation data")
    if history:
        reasons.append("has historical approval/audit activity")
    if duplicate_email:
        usernames = ", ".join(sorted({user.get("username", "?") for user in matches}))
        reasons.append(f"email already exists in Keycloak as {usernames}")
    if not reasons:
        reasons.append("no activity or references")
    return action, reasons


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--json", action="store_true", help="emit JSON instead of a table")
    parser.add_argument(
        "--fail-on-orphans",
        action="store_true",
        help="exit with status 2 when orphaned profiles exist",
    )
    args = parser.parse_args()

    keycloak = keycloak_users()
    subjects = {user["id"] for user in keycloak}
    by_email: dict[str, list[dict]] = {}
    for user in keycloak:
        email = (user.get("email") or "").lower()
        if email:
            by_email.setdefault(email, []).append(user)

    orphans = [row for row in profile_rows() if row["subject"] not in subjects]
    for row in orphans:
        row["action"], row["reasons"] = classify(row, by_email)

    if args.json:
        print(json.dumps(orphans, indent=2))
    elif not orphans:
        print("No orphaned user profiles found.")
    else:
        print(f"DRY RUN: {len(orphans)} orphaned profile(s); no data was changed.\n")
        header = (
            f"{'SUBJECT':38} {'NAME':22} {'EMAIL':30} {'ORG':12} "
            f"{'REQ':>3} {'PO':>3} {'ACT':>3} {'AUD':>4} {'MGR':>3} {'DEL':>3} {'FAV':>3}  ACTION"
        )
        print(header)
        print("-" * len(header))
        for row in orphans:
            print(
                f"{row['subject'][:38]:38} {row['name'][:22]:22} {row['email'][:30]:30} "
                f"{row['organization'][:12]:12} {row['requests']:>3} {row['purchase_orders']:>3} "
                f"{row['approval_actions']:>3} {row['audit_events']:>4} {row['manager_reports']:>3} "
                f"{row['delegations']:>3} {row['catalog_favorites']:>3}  {row['action']}"
            )
        print("\nReasons:")
        for row in orphans:
            print(f"- {row['subject']}: {row['action']} — {'; '.join(row['reasons'])}")
        print("\nRun with --json for machine-readable output.")

    if args.fail_on_orphans and orphans:
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
