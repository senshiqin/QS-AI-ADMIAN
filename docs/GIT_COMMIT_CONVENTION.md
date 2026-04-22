# Git Commit Convention

Use `type(scope): subject` format:

```text
feat(ai): add chat response DTO
fix(user): handle null email
refactor(service): split ai prompt builder
docs(readme): update startup guide
chore(build): upgrade plugin version
```

Recommended `type` values:

- `feat`: new feature
- `fix`: bug fix
- `refactor`: code cleanup without behavior change
- `docs`: documentation change
- `test`: test case change
- `chore`: build/tooling/dependency change

Rules:

- Keep `subject` short and clear (<= 72 chars preferred).
- Use imperative mood, e.g. `add`, `fix`, `remove`.
- One commit should solve one independent concern.

First commit suggestion:

```text
chore(init): bootstrap springboot ai backend scaffold
```
