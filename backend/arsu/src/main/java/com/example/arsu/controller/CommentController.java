package com.example.arsu.controller;

import com.example.arsu.model.Comment;
import com.example.arsu.service.CommentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/comments")
public class CommentController {
    @Autowired
    private CommentService commentService;

    @PostMapping("/add")
    public Comment addComment(@RequestBody Comment comment) {
        return commentService.addComment(comment);
    }

    @GetMapping("/video/{videoId}")
    public List<Comment> getCommentsByVideoId(@PathVariable String videoId) {
        return commentService.getCommentsByVideoId(videoId);
    }
}

