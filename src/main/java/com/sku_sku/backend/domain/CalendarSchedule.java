package com.sku_sku.backend.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
@Entity // 캘린더 일정
public class CalendarSchedule {
    @Id
    @GeneratedValue
    private Long id;// pk

    private String title; // 캘린더 일정 제목

    private LocalDate startDate; // YYYY-MM-DD // 일정 시작 시점

    private LocalDate endDate; // YYYY-MM-DD // 일정 종료 시점

    private String color; // 일정 표시 색상

    public CalendarSchedule(String title, LocalDate startDate, LocalDate endDate, String color) {
        this.title = title;
        this.startDate = startDate;
        this.endDate = endDate;
        this.color = color;
    }

    public void update(String title, LocalDate startDate, LocalDate endDate, String color) {
        this.title = title;
        this.startDate = startDate;
        this.endDate = endDate;
        this.color = color;
    }}
