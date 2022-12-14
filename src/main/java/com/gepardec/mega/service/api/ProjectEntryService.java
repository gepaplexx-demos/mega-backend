package com.gepardec.mega.service.api;

import com.gepardec.mega.db.entity.project.ProjectEntry;
import com.gepardec.mega.rest.model.ProjectEntryDto;

import java.time.LocalDate;
import java.util.List;

public interface ProjectEntryService {
    List<ProjectEntry> findByNameAndDate(final String projectName, final LocalDate from, final LocalDate to);

    boolean update(ProjectEntryDto projectEntry);
}
