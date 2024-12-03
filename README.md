## Task 5

**Cost**: 20 points.

Make an application that contains business logic for making exchange operations between different currencies.

1. Create models for dealing with currencies, user accounts and exchange rates. One account can have multiple currency
   values. Use `BigDecimal` for performing of exchange calculations.
2. Data with user accounts should be stored as files (one file per account).
3. Separate application functionality to DAO, service and utilities.
4. Create module which will provide high-level operations (manage accounts, currencies, exchange rates).
5. Create sample accounts and currencies. Define sample exchange rates.
6. Provide concurrent data access to user accounts. Simulate simultaneous currency exchanges for single account by
   multiple threads and ensure that all the operations are thread-safe.
7. Use `ExecutorService` to manage threads.
8. Make custom exceptions to let user to know the reason of error. Do not handle runtime exceptions.
9. Validate inputs such an account existence, sufficiency of currency amount, etc.
10. Log information about what is happening on different application levels and about conversion results. Use `Logger`
    for that.