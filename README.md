# AttendEase — Employee Attendance & Leave Management System

AttendEase is a Java web application that helps administrators manage employee records, track attendance, process leave requests, and generate monthly reports.

## Features

- Secure login for administrators and employees
- Role-based access control
- Add, search, filter, and edit employee records
- Employee attendance management
- Leave request submission and approval/rejection
- Monthly attendance reports and CSV export
- Password hashing, session management, CSRF protection, and prepared SQL statements

## Technologies

- Java 21
- Jakarta Servlets
- MySQL
- Apache Tomcat 10.1
- Maven
- HTML, CSS, JavaScript

## Prerequisites

- JDK 21
- Apache Maven
- MySQL Server
- Apache Tomcat 10.1

## Build

From the project directory, run:

```bash
mvn clean package
```

The generated WAR file will be located at `target/attendance.war`.

## Run Locally

1. Create the `attendance_db` database in MySQL.
2. Configure the environment variables `ATTENDANCE_DB_URL`, `ATTENDANCE_DB_USER`, and `ATTENDANCE_DB_PASSWORD`.
3. Ensure the required database tables exist.
4. Build the project with Maven.
5. Deploy `target/attendance.war` to Tomcat's `webapps` directory.
6. Start Tomcat and visit `http://localhost:8080/attendance/`.

## Security

The application uses hashed passwords, server-side role checks, session invalidation on logout, CSRF validation, and prepared SQL statements.

## Project Status

The core authentication, employee management, attendance, leave management, and reporting workflows have been implemented and manually tested.