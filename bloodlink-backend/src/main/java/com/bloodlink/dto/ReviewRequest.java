package com.bloodlink.dto;

public class ReviewRequest {
    private String userId;
    private Integer rating;
    private String text;

    public ReviewRequest() {}

    public ReviewRequest(String userId, Integer rating, String text) {
        this.userId = userId;
        this.rating = rating;
        this.text = text;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
}