
package com.attendance;

public class Employee {
    private int id;
    private String name;
    private String email;
    private String department;
    private String jobRole;

    public Employee(int id, String name, String email,
                    String department, String jobRole) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.department = department;
        this.jobRole = jobRole;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getDepartment() {
        return department;
    }

    public String getJobRole() {
        return jobRole;
    }
}
