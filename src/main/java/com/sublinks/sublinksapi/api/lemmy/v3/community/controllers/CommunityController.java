package com.sublinks.sublinksapi.api.lemmy.v3.community.controllers;

import com.sublinks.sublinksapi.api.lemmy.v3.authentication.JwtPerson;
import com.sublinks.sublinksapi.api.lemmy.v3.community.models.CommunityModeratorView;
import com.sublinks.sublinksapi.api.lemmy.v3.community.models.CommunityResponse;
import com.sublinks.sublinksapi.api.lemmy.v3.community.models.CommunityView;
import com.sublinks.sublinksapi.api.lemmy.v3.community.models.FollowCommunity;
import com.sublinks.sublinksapi.api.lemmy.v3.community.models.GetCommunity;
import com.sublinks.sublinksapi.api.lemmy.v3.community.models.GetCommunityResponse;
import com.sublinks.sublinksapi.api.lemmy.v3.community.models.ListCommunities;
import com.sublinks.sublinksapi.api.lemmy.v3.community.models.ListCommunitiesResponse;
import com.sublinks.sublinksapi.api.lemmy.v3.community.services.LemmyCommunityService;
import com.sublinks.sublinksapi.api.lemmy.v3.site.models.Site;
import com.sublinks.sublinksapi.community.dto.Community;
import com.sublinks.sublinksapi.community.repositories.CommunityRepository;
import com.sublinks.sublinksapi.instance.models.LocalInstanceContext;
import com.sublinks.sublinksapi.person.dto.Person;
import com.sublinks.sublinksapi.person.enums.LinkPersonCommunityType;
import com.sublinks.sublinksapi.person.services.LinkPersonCommunityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.ConversionService;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/api/v3/community")
public class CommunityController {
    private final LocalInstanceContext localInstanceContext;
    private final CommunityRepository communityRepository;
    private final LemmyCommunityService lemmyCommunityService;
    private final LinkPersonCommunityService linkPersonCommunityService;
    private final ConversionService conversionService;

    @GetMapping
    public GetCommunityResponse show(@Valid final GetCommunity getCommunityForm, final JwtPerson principal) {

        final Community community = communityRepository.findCommunityByIdOrTitleSlug(
                getCommunityForm.id(), getCommunityForm.name()
        );
        CommunityView communityView;
        if (principal != null) {
            Person person = (Person) principal.getPrincipal();
            communityView = lemmyCommunityService.communityViewFromCommunity(community, person);
        } else {
            communityView = lemmyCommunityService.communityViewFromCommunity(community);
        }
        final List<CommunityModeratorView> moderatorViews = lemmyCommunityService.communityModeratorViewList(community);
        return GetCommunityResponse.builder()
                .community_view(communityView)
                .site(conversionService.convert(localInstanceContext, Site.class))
                .moderators(moderatorViews)
                .discussion_languages(lemmyCommunityService.communityLanguageCodes(community))
                .build();
    }

    @GetMapping("list")
    @Transactional
    public ListCommunitiesResponse list(@Valid final ListCommunities listCommunitiesForm, final JwtPerson principal) {

        final Collection<CommunityView> communityViews = new HashSet<>();

        final Collection<Community> communities = communityRepository.findAll(); // @todo apply filters
        for (Community community : communities) {
            CommunityView communityView;
            if (principal != null) {
                Person person = (Person) principal.getPrincipal();
                communityView = lemmyCommunityService.communityViewFromCommunity(community, person);
            } else {
                communityView = lemmyCommunityService.communityViewFromCommunity(community);
            }
            communityViews.add(communityView);
        }

        return ListCommunitiesResponse.builder()
                .communities(communityViews)
                .build();
    }

    @PostMapping("follow")
    CommunityResponse follow(@Valid @RequestBody final FollowCommunity followCommunityForm, final JwtPerson principal) {

        final Person person = (Person) principal.getPrincipal();

        final Optional<Community> community = communityRepository.findById((long) followCommunityForm.community_id());

        if (community.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }

        if (followCommunityForm.follow()) {
            linkPersonCommunityService.addLink(person, community.get(), LinkPersonCommunityType.follower);
        } else {
            linkPersonCommunityService.removeLink(person, community.get(), LinkPersonCommunityType.follower);
        }

        return lemmyCommunityService.createCommunityResponse(
                community.get(),
                person
        );
    }
}