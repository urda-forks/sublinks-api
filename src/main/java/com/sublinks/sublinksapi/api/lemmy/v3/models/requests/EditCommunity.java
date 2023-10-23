package com.sublinks.sublinksapi.api.lemmy.v3.models.requests;

import lombok.Builder;

import java.util.Collection;

@Builder
public record EditCommunity(
        Integer community_id,
        String title,
        String description,
        String icon,
        String banner,
        Boolean nsfw,
        Boolean posting_restricted_to_mods,
        Collection<String> discussion_languages
) {
}