package com.example.project013982.comment;

/**
 * Body of POST and PUT comment requests: {@code {"content": "..."}}.
 *
 * <p>content is declared as Object on purpose. A String field would let JSON numbers or booleans
 * be coerced into text, but the contract requires a JSON string; CommentService rejects anything else.
 * Unknown fields such as authorId or userId are ignored: the author always comes from the session.
 */
public record CommentRequest(Object content) {
}
