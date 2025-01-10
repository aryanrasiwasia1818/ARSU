
package com.example.arsu.service;

import com.example.arsu.model.Comment;
import com.example.arsu.repository.CommentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
public class CommentService {
    @Autowired
    private CommentRepository commentRepository;

    public Comment addComment(Comment comment) {
        comment.setCreatedAt(new Date());
        comment.setUpdatedAt(new Date());
        return commentRepository.save(comment);
    }

    public List<Comment> getCommentsByVideoId(String videoId) {
        return commentRepository.findByVideoId(videoId);
    }
}
