package com.sublinks.sublinksapi.api.lemmy.v3.comment.controllers;

import com.sublinks.sublinksapi.api.lemmy.v3.admin.models.RegistrationApplicationResponse;
import com.sublinks.sublinksapi.api.lemmy.v3.authentication.JwtPerson;
import com.sublinks.sublinksapi.api.lemmy.v3.comment.models.CommentReportResponse;
import com.sublinks.sublinksapi.api.lemmy.v3.comment.models.CommentResponse;
import com.sublinks.sublinksapi.api.lemmy.v3.comment.models.CommentView;
import com.sublinks.sublinksapi.api.lemmy.v3.comment.models.CreateComment;
import com.sublinks.sublinksapi.api.lemmy.v3.comment.models.CreateCommentLike;
import com.sublinks.sublinksapi.api.lemmy.v3.comment.models.EditComment;
import com.sublinks.sublinksapi.api.lemmy.v3.comment.models.GetComments;
import com.sublinks.sublinksapi.api.lemmy.v3.comment.models.GetCommentsResponse;
import com.sublinks.sublinksapi.api.lemmy.v3.comment.models.MarkCommentReplyAsRead;
import com.sublinks.sublinksapi.api.lemmy.v3.comment.services.LemmyCommentService;
import com.sublinks.sublinksapi.api.lemmy.v3.common.controllers.AbstractLemmyApiController;
import com.sublinks.sublinksapi.authorization.enums.AuthorizeAction;
import com.sublinks.sublinksapi.authorization.services.AuthorizationService;
import com.sublinks.sublinksapi.comment.dto.Comment;
import com.sublinks.sublinksapi.comment.enums.CommentSortType;
import com.sublinks.sublinksapi.comment.models.CommentSearchCriteria;
import com.sublinks.sublinksapi.comment.repositories.CommentRepository;
import com.sublinks.sublinksapi.comment.services.CommentLikeService;
import com.sublinks.sublinksapi.comment.services.CommentReadService;
import com.sublinks.sublinksapi.comment.services.CommentService;
import com.sublinks.sublinksapi.language.dto.Language;
import com.sublinks.sublinksapi.language.repositories.LanguageRepository;
import com.sublinks.sublinksapi.person.dto.Person;
import com.sublinks.sublinksapi.person.enums.ListingType;
import com.sublinks.sublinksapi.person.services.PersonService;
import com.sublinks.sublinksapi.post.dto.Post;
import com.sublinks.sublinksapi.post.repositories.PostRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.ConversionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/api/v3/comment")
@Tag(name = "Comment")
public class CommentController extends AbstractLemmyApiController {
    private final CommentRepository commentRepository;
    private final CommentService commentService;
    private final LemmyCommentService lemmyCommentService;
    private final PostRepository postRepository;
    private final LanguageRepository languageRepository;
    private final ConversionService conversionService;
    private final CommentLikeService commentLikeService;
    private final PersonService personService;
    private final CommentReadService commentReadService;
    private final AuthorizationService authorizationService;

    @Operation(summary = "Create a comment.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = { @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = CommentResponse.class))})
    })
    @PostMapping
    @Transactional
    public CommentResponse create(@Valid @RequestBody final CreateComment createCommentForm, final JwtPerson principal) {

        // @todo auth service
        final Person person = getPersonOrThrowUnauthorized(principal);
        final Post post = postRepository.findById((long) createCommentForm.post_id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST));
        // Language
        Optional<Language> language;
        if (createCommentForm.language_id() != null) {
            language = languageRepository.findById((long) createCommentForm.language_id());
        } else {
            language = personService.getPersonDefaultPostLanguage(person, post.getCommunity());
        }
        if (language.isEmpty()) {
            throw new RuntimeException("No language selected");
        }
        final Comment comment = Comment.builder()
                .person(person)
                .isLocal(true)
                .commentBody(createCommentForm.content())
                .activityPubId("")
                .post(post)
                .community(post.getCommunity())
                .language(language.get())
                .build();

        if (createCommentForm.parent_id() != null) {
            Optional<Comment> parentComment = commentRepository.findById((long) createCommentForm.parent_id());
            if (parentComment.isEmpty()) {
                throw new RuntimeException("Invalid comment parent.");
            }
            commentService.createComment(comment, parentComment.get());
        } else {
            commentService.createComment(comment);
        }
        commentLikeService.updateOrCreateCommentLikeLike(comment, person);

        final CommentView commentView = lemmyCommentService.createCommentView(comment, person);
        return CommentResponse.builder()
                .comment_view(commentView)
                .recipient_ids(new ArrayList<>())
                .build();
    }

    @Operation(summary = "Edit a comment.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = { @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = CommentResponse.class))})
    })
    @PutMapping
    CommentResponse update(@Valid @RequestBody final EditComment editCommentForm, final JwtPerson principal) {

        final Person person = getPersonOrThrowUnauthorized(principal);
        Comment comment = commentRepository.findById((long) editCommentForm.comment_id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST));
        authorizationService
                .canPerson(person)
                .performTheAction(AuthorizeAction.update)
                .onEntity(comment)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));

        comment.setCommentBody(editCommentForm.content());
        Optional<Language> language;
        if (editCommentForm.language_id() != null) {
            language = languageRepository.findById((long) editCommentForm.language_id());
        } else {
            language = personService.getPersonDefaultPostLanguage(person, comment.getPost().getCommunity());
        }
        if (language.isEmpty()) {
            throw new RuntimeException("No language selected");
        }
        comment.setLanguage(language.get());

        commentService.updateComment(comment);

        final CommentView commentView = lemmyCommentService.createCommentView(comment, person);
        return CommentResponse.builder()
                .comment_view(commentView)
                .recipient_ids(new ArrayList<>())
                .build();
    }

    @Operation(summary = "Delete a comment.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = { @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = CommentResponse.class))})
    })
    @PostMapping("delete")
    CommentResponse delete() {

        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }

    @Operation(summary = "Mark a comment as read.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = { @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = CommentResponse.class))})
    })
    @PostMapping("mark_as_read")
    CommentResponse markAsRead(@Valid @RequestBody final MarkCommentReplyAsRead markCommentReplyAsRead, final JwtPerson principal) {

        final Comment comment = commentRepository.findById((long) markCommentReplyAsRead.comment_reply_id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST));
        final Person person = getPersonOrThrowBadRequest(principal);
        commentReadService.markCommentReadByPerson(comment, person);

        final CommentView commentView = lemmyCommentService.createCommentView(comment, person);
        return CommentResponse.builder()
                .comment_view(commentView)
                .recipient_ids(new ArrayList<>())
                .build();
    }

    @Operation(summary = "Like / Vote on a comment.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = { @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = CommentResponse.class))})
    })
    @PostMapping("like")
    CommentResponse like(@Valid @RequestBody CreateCommentLike createCommentLikeForm, JwtPerson principal) {

        final Person person = getPersonOrThrowUnauthorized(principal);
        final Comment comment = commentRepository.findById(createCommentLikeForm.comment_id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST));
        if (createCommentLikeForm.score() == 1) {
            commentLikeService.updateOrCreateCommentLikeLike(comment, person);
        } else if (createCommentLikeForm.score() == -1) {
            commentLikeService.updateOrCreateCommentLikeDislike(comment, person);
        } else {
            commentLikeService.updateOrCreateCommentLikeNeutral(comment, person);
        }
        final CommentView commentView = lemmyCommentService.createCommentView(comment, person);
        return CommentResponse.builder()
                .comment_view(commentView)
                .recipient_ids(new ArrayList<>())
                .build();
    }

    @Operation(summary = "Save a comment for later.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = { @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = CommentResponse.class))})
    })
    @PutMapping("save")
    CommentResponse saveForLater() {

        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }

    @Operation(summary = "Get / fetch comments.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = { @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = GetCommentsResponse.class))})
    })
    @GetMapping("list")
    GetCommentsResponse list(@Valid final GetComments getCommentsForm, final JwtPerson principal) {

        final Post post = postRepository.findById((long) getCommentsForm.post_id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST));

        Optional<Person> person = getOptionalPerson(principal);

        final CommentSortType sortType = conversionService.convert(getCommentsForm.sort(), CommentSortType.class);
        final ListingType listingType = conversionService.convert(getCommentsForm.type_(), ListingType.class);

        final CommentSearchCriteria commentRepositorySearch = CommentSearchCriteria.builder()
                .page(1)
                .listingType(listingType)
                .perPage(20)
                .commentSortType(sortType)
                .post(post)
                .build();

        final List<Comment> comments = commentRepository.allCommentsBySearchCriteria(commentRepositorySearch);
        final List<CommentView> commentViews = new ArrayList<>();
        for (Comment comment : comments) {
            CommentView commentView;
            if (person.isPresent()) {
                commentView = lemmyCommentService.createCommentView(comment, person.get());
                commentReadService.markCommentReadByPerson(comment, person.get());
            } else {
                commentView = lemmyCommentService.createCommentView(comment);
            }
            commentViews.add(commentView);
        }
        return GetCommentsResponse.builder().comments(commentViews).build();
    }

    @Operation(summary = "Report a comment.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = { @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = CommentReportResponse.class))})
    })
    @PostMapping("report")
    CommentReportResponse report() {

        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }
}
