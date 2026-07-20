# write up 

## 1. What did you ask the AI to do, and what did you write or decide yourself?

I used AI throughout the assignment to speed up implementation, especially for boilerplate code like DTOs, controllers, tests, and some Spring Boot configuration. It was also helpful for reviewing the code and suggesting cleaner ways to structure a few classes.

The overall design and architecture were decisions I made. I decided to separate Job and JobExecution into different entities, use a dynamic scheduler instead of static @Scheduled methods, and keep scheduling logic separate from CRUD operations. I rarely accepted generated code without reading it first. I usually refactored names, adjusted the structure, or simplified parts of the implementation before committing them.

## 2. Where did you override, correct, or throw away the AI's output — and why?

One place where I intentionally went in a different direction was the scheduling approach. An initial suggestion was to use Spring's @Scheduled, but that didn't fit the problem because jobs are created and updated at runtime. I instead implemented scheduling using TaskScheduler, CronTrigger, and ScheduledFuture so that jobs can be added, updated, activated, or deleted without restarting the application.

I also made a conscious effort to keep responsibilities separate. Instead of letting controllers or the scheduler directly interact with the database, I kept business logic inside the service layer and used events to notify the scheduler when jobs changed. That made the code easier to reason about and test.

## 3. The two or three biggest trade-offs I made, and the alternatives I considered

Dynamic scheduling instead of @Scheduled
I chose to use Spring's TaskScheduler with CronTrigger because the schedules are stored in the database and can change while the application is running. @Scheduled would have been simpler, but it isn't designed for dynamically managed jobs.

Separate execution history
I created a separate JobExecution entity instead of storing the latest execution status inside the Job entity. This keeps the model cleaner and allows every execution to be recorded with its own status, timestamps, and duration, which also satisfies the assignment requirement to query run history.

H2 instead of PostgreSQL
I chose H2 for this assignment because it makes the project much easier to run. Reviewers don't need to install or configure an external database, and the application is ready to use immediately after cloning the repository. Since the assignment wasn't focused on database-specific features, I felt this was a reasonable trade-off.

## 4. What's missing, or what I'd do with another day?

With more time, I'd add a few production-oriented improvements. I'd use Flyway for database migrations instead of relying on automatic schema creation, add retry support and configurable failure handling for scheduled jobs, improve monitoring with Spring Boot Actuator, and add pagination and filtering for execution history. I'd also increase test coverage around concurrent execution and scheduler edge cases.


