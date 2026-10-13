package com.parosurvivors.notifications.infrastructure.repositories;

import com.parosurvivors.notifications.domain.DeliveryStatus;
import com.parosurvivors.notifications.infrastructure.entities.NotificationDeliveryEntity;
import com.parosurvivors.notifications.infrastructure.entities.NotificationEntity;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class NotificationDeliverySpecification {

    private NotificationDeliverySpecification() {}

    public static Specification<NotificationDeliveryEntity> fromQuery(Long userId, Boolean read, Long channelId, DeliveryStatus status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            query.distinct(true);

            Subquery<Long> notifSubquery = query.subquery(Long.class);
            Root<NotificationEntity> notifRoot = notifSubquery.from(NotificationEntity.class);
            notifSubquery.select(notifRoot.get("id"))
                    .where(cb.equal(notifRoot.get("userId"), userId));
            predicates.add(root.get("notificationId").in(notifSubquery));

            if (read != null) {
                if (read) {
                    predicates.add(cb.isNotNull(root.get("readAt")));
                } else {
                    predicates.add(cb.isNull(root.get("readAt")));
                }
            }

            if (channelId != null) {
                predicates.add(cb.equal(root.get("channelId"), channelId.intValue()));
            }

            if (status != null) {
                predicates.add(cb.equal(root.get("deliveryStatus"), status));
            }

            query.orderBy(cb.desc(root.get("id")));

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
