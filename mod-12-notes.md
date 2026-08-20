# Module 12 Lab — Team Repo & Branching Strategy Setup

Submission record confirming completion of the Module 12 lab, dated 2026-08-20.

## Part A — Repository created
The team agreed the repository name `jeepers-leapers` and created it on GitHub as a public, empty repository (no auto-generated README, `.gitignore`, or licence). All team members were added as collaborators and each cloned the repository locally, confirming they could see it.

- Repository URL: https://github.com/anikatay/jeepers-leapers.git

## Part B — Branching strategy agreed
The team agreed on **trunk-based development with short-lived feature branches**, reviewed via pull request before merging into `main`.

This was chosen because:
- It's easier for the team to work in parallel without dealing with merge conflicts
- It keeps process overhead low, which suits a 10-week project
- It still allows use of PRs and issues for review and future planning

## Part C — Documented and pushed
`README.md` at the repository root was updated with a one-line project description, a `## Branching Strategy` section, and a `## Team` section, then committed and pushed. Every other team member pulled the change and confirmed they could see the updated README.

## Acceptance criteria — evidence
- The repository exists on GitHub with every team member able to push, confirmed by someone other than the README's author making a trivial commit and pushing it.
- `README.md` contains a `## Branching Strategy` section naming the strategy and the reason for choosing it.
- Every team member's local clone is up to date with the pushed README.

