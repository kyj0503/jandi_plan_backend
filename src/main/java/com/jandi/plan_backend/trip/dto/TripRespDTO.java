package com.jandi.plan_backend.trip.dto;

import com.jandi.plan_backend.trip.entity.Trip;
import com.jandi.plan_backend.user.entity.User;
import lombok.Getter;
import java.time.LocalDate;

@Getter
public class TripRespDTO {
    private final UserTripDTO user;
    private final Integer tripId;
    private final String title;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final Integer likeCount;
    private final Integer budget;
    private final Integer cityId;
    private final String cityName;
    private final String countryName;
    private final Boolean privatePlan;
    private final String cityImageUrl;

    public TripRespDTO(UserTripDTO user, Integer tripId, String title, LocalDate startDate, LocalDate endDate,
                       Integer likeCount, Integer budget, Integer cityId, String cityName, String countryName,
                       Boolean privatePlan, String cityImageUrl) {
        this.user = user;
        this.tripId = tripId;
        this.title = title;
        this.startDate = startDate;
        this.endDate = endDate;
        this.likeCount = likeCount;
        this.budget = budget;
        this.cityId = cityId;
        this.cityName = cityName;
        this.countryName = countryName;
        this.privatePlan = privatePlan;
        this.cityImageUrl = cityImageUrl;
    }

    public TripRespDTO(User user, String userProfileUrl, Trip trip, String cityImageUrl) {
        this.user = new UserTripDTO(user.getUserId(), user.getUserName(), userProfileUrl);
        this.tripId = trip.getTripId();
        this.title = trip.getTitle();
        this.startDate = trip.getStartDate();
        this.endDate = trip.getEndDate();
        this.likeCount = trip.getLikeCount();
        this.budget = trip.getBudget();
        this.cityId = trip.getCity().getCityId();
        this.cityName = trip.getCity().getName();
        this.countryName = trip.getCity().getCountry().getName();
        this.privatePlan = trip.getPrivatePlan();
        this.cityImageUrl = cityImageUrl;
    }
}
