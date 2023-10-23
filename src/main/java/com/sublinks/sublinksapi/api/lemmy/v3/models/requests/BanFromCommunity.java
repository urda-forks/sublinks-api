package com.sublinks.sublinksapi.api.lemmy.v3.models.requests;

import lombok.Builder;

@Builder
public record BanFromCommunity(
        Integer community_id,
        Integer person_id,
        Boolean ban,
        Boolean remove_data,
        String reason,
        Integer expires
) {
}