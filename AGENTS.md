# Working agreement

Work autonomously on requested tasks within this repository.
Keep changes focused and preserve unrelated
work.

Within the scope of requested work, no extra confirmation is needed to:

- Inspect and edit project source, tests, documentation, and build files.
- Run builds, tests, formatting, and local checks.
- Fix compilation and test failures related to the requested work.

Ask before:

- Changing files outside this repository.
- Installing software globally or changing user-wide settings.
- Adding or upgrading production dependencies.
- Deleting unrelated work or performing destructive Git actions.
- Committing, pushing, publishing, or creating releases.
- Accessing credentials or sending project data to external services.

These are behavior instructions, not a grant of filesystem or network permission.
Honor sandbox controls and request the specific scope needed when additional
permissions are required. Previously authorized temporary Gradle tooling may be
used within its approved scope; that authorization does not extend to additional
writes outside this repository.

Report the changes made and the checks that actually passed. Clearly identify
failed or blocked checks, and do not present unrun checks as successful.
