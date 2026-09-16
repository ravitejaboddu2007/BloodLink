package com.bloodlink.dto;

public class ReviewDTO {
    private String id;
    private String userId;
    private String role;
    private String name;
    private Integer rating;
    private String text;
    private String createdAt;

    public ReviewDTO() {}

    public ReviewDTO(String id, String userId, String role, String name, Integer rating, String text, String createdAt) {
        this.id = id;
        this.userId = userId;
        this.role = role;
        this.name = name;
        this.rating = rating;
        this.text = text;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}