package com.parosurvivors.serviya.services.domain;

import com.parosurvivors.serviya.metrics.domain.OffererMetrics;
import com.parosurvivors.serviya.metrics.domain.ServiceMetrics;
import com.parosurvivors.serviya.profiles.domain.OffererProfile;
import com.parosurvivors.serviya.profiles.domain.OffererProfileSummary;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceDetail {
    private Service service;
    private Category category;
    private OffererProfile offererProfile;
    private OffererProfileSummary offererSummary;
    private List<FeedbackUser> feedbackUsers;
    private List<ServiceAvailability> availability;
    private ServiceMetrics serviceMetrics;
    private OffererMetrics offererMetrics;
}
