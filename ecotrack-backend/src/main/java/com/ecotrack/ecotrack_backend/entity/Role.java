package com.ecotrack.ecotrack_backend.entity;

import jakarta.persistence.*;

/**
 * Entity representing user authorization roles within the application.
 * Maps directly to the 'roles' table in MySQL.
 */
@Entity
@Table(name = "roles")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    // Default constructor required by JPA
    public Role() {
    }

    // Parameterized constructor for ease of use
    public Role(String name) {
        this.name = name;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}