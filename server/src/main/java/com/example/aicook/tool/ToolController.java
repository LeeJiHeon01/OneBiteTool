package com.example.aicook.tool;

import com.example.aicook.reference.ReferenceData;
import com.example.aicook.reference.Tools;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tools")
public class ToolController {

    private final ReferenceData reference;

    public ToolController(ReferenceData reference) {
        this.reference = reference;
    }

    /** Tool categories and choices with their volumes, cup dimensions, fill levels and value source. */
    @GetMapping
    public List<Tools.Category> tools() {
        return reference.toolCategories();
    }
}
