# AGENTS.md — Project Instructions

## Project goal

Build a student course project called **Debt and Payment Accounting System** (Kazakh topic: **«Қарыздар мен төлемдерді есепке алу жүйесін әзірлеу»**).

The application lets a user manually record debts or loans, register payments, calculate remaining balances, and view simple summaries. It is an educational desktop application, not a production banking or government system.

**Important scope decision: no external APIs.** Do not integrate with banks, tax authorities, government services, credit bureaus, or third-party debt services. The user explicitly abandoned the API idea. All debt and payment data is entered or edited inside the application.

## Technology

- Java for application and business logic.
- JavaFX for the desktop user interface.
- Supabase PostgreSQL for persistent data storage.
- Maven if the repository does not already use another build system.
- A Flutter mobile client is only a possible future extension. Do not build it now unless explicitly requested.

Before changing anything, inspect the existing repository, JDK, build system, and code. Preserve working code and follow existing conventions.

## Architecture: business logic without over-engineering

Use a simple layered architecture:

1. **Presentation layer** — JavaFX views and controllers.
2. **Service/business layer** — business rules, validation, calculations.
3. **Repository/data-access layer** — database reads and writes.
4. **Model layer** — classes such as `Debt` and `Payment`.

Typical flow:

`JavaFX View → Controller → Service → Repository → Supabase PostgreSQL`

Keep SQL and database access out of JavaFX button handlers. Keep calculations out of UI controllers. Business rules should be understandable and testable without launching JavaFX.

Use business architecture, but keep it appropriate for a beginner student project. Do not add microservices, unnecessary design patterns, excessive interfaces, or complicated dependency-injection frameworks without a clear need.

## Core features

Implement incrementally:

- Create, view, edit, and delete debt records.
- Store a debt title/description, creditor name, original amount, creation date, due date, optional interest rate, status, and optional notes.
- Record payments linked to a debt.
- View payment history for each debt.
- Calculate total paid and remaining balance.
- Identify overdue debts using due date and remaining balance.
- Show a dashboard with total debt, total paid, remaining balance, and overdue count.
- Search or filter debts by title, creditor, status, or date when practical.
- Validate user input and show understandable error messages.
- Confirm before destructive actions such as deleting a record.

Do not silently invent complicated interest, penalty, or tax rules. Start with basic debt and payment tracking. If interest calculations are later added, define the rules clearly and test them. Do not describe the application as an official tax calculator.

## Code style and comments

- Write straightforward Java that a student who has studied Java can understand.
- Use clear class, method, and variable names.
- Keep methods focused and reasonably short.
- Prefer clear code over clever shortcuts.
- Avoid unnecessary abstractions and over-engineering.
- Add comments **only for important business rules, non-obvious decisions, or security-sensitive details**.
- Do not add comments that merely repeat what the code already says.
- Handle exceptions explicitly; never ignore errors or leave empty catch blocks.
- Use `BigDecimal` for money, not `double`.
- Use suitable Java date/time types.
- Keep UI, business rules, and persistence separate.

## Database and security

- Use Supabase PostgreSQL as the database.
- Create clear tables with primary keys, foreign keys, sensible constraints, and required fields.
- Each payment must reference an existing debt.
- Store individual payment records and calculate totals from them, unless a cached total has a clear reason and is kept consistent.
- Use sample data during development.
- Do not commit credentials, database passwords, or tokens to Git.
- Never embed a Supabase `service_role` key in the JavaFX application. Desktop application code can be inspected by users.
- Configure access control appropriately. Do not expose the database publicly just to make the application connect.
- Choose an access method with restricted permissions and document its security limitations. If direct PostgreSQL access is used for local development, do not present it as production-safe.
- Handle network/database failures gracefully and tell the user when a read or save failed.

## Suggested screens

- Dashboard
- Debts list
- Debt details and payment history
- Add/edit debt form
- Add payment form
- Basic reports or summaries

Build a practical finance-management interface, not a marketing landing page. Prioritize readable tables, clear labels, consistent spacing, and useful empty/loading/error states. Avoid unnecessary animation and decoration.

## Workflow for Codex

1. Inspect the repository and summarize its current structure before editing.
2. Identify the existing JDK and build setup.
3. Implement one small milestone at a time.
4. Explain which files changed and how to run or test the result.
5. Do not generate the whole application in one huge step.
6. Ask before changing the agreed stack or adding major features.
7. Keep `README.md` setup instructions current.
8. Add basic tests for calculations and validation where practical.

Suggested milestones:
1. Project skeleton and JavaFX startup.
2. Model classes and business rules.
3. Supabase schema and database-access setup.
4. Debt CRUD operations.
5. Payments and remaining-balance calculation.
6. Dashboard, filters, and error handling.
7. Tests, README, and final cleanup.

## Future mobile version

Flutter is a possible later extension, not part of the first milestone. Keep business rules independent of JavaFX where practical so they can be reused or reimplemented later. Do not build Flutter or a Java REST API server unless explicitly requested.

## Definition of done

A feature is complete when it works in the UI, validates input, handles errors clearly, persists data when required, keeps business logic out of the UI, and remains understandable to a beginner.
