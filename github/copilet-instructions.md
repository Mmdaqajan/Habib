
# Project Development Rules

## General Principles
- Understand the existing architecture before making changes.
- Prefer minimal, focused changes.
- Do not rewrite working code without a clear reason.
- Do not create unnecessary files or dependencies.
- Never assume a file, function, or class exists. Inspect it first.

## Code Quality
- Follow PEP 8.
- Use clear naming and modular architecture.
- Avoid duplicated code.
- Keep functions and classes focused.
- Handle errors explicitly.

## Django
- Follow Django conventions.
- Use Django ORM appropriately.
- Keep business logic separate from views.
- Use serializers and validation properly in DRF.
- Avoid unnecessary database queries.
- Use transactions where necessary.

## Testing
- Write tests for new functionality.
- Test edge cases and failure scenarios.
- Run relevant tests after changes.
- Never claim tests passed without executing them.

## Git
- Do not delete existing branches.
- Do not reset or overwrite user changes.
- Never commit without explicit approval.
- Keep changes limited to the requested task.

## Agent Workflow
1. Inspect relevant files.
2. Explain the implementation plan.
3. Identify potential risks.
4. Implement the smallest suitable solution.
5. Run tests and report results.
6. Summarize changed files and decisions.
