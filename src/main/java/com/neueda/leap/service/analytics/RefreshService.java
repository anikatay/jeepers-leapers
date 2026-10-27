package com.neueda.leap.service.analytics;

import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.neueda.leap.client.AnalyticsEtlClient;
import com.neueda.leap.client.EtlRefreshResult;
import com.neueda.leap.exception.ObjectAlreadyExistsException;
import com.neueda.leap.exception.ObjectInvalidException;
import com.neueda.leap.exception.ObjectNotFoundException;
import com.neueda.leap.exception.ObjectNotProcessedException;
import com.neueda.leap.mapper.analytics.RefreshMapper;
import com.neueda.leap.model.analytics.Refresh;
import com.neueda.leap.model.analytics.projection.RefreshProjection;

@Service("analyticsRefreshService")
public class RefreshService {

    private static final Pattern RUN_ID_PATTERN = Pattern.compile("^[A-Za-z0-9._:-]{1,255}$");

    private final RefreshMapper refreshMapper;
    private final AnalyticsEtlClient etlClient;

    public RefreshService(
            RefreshMapper refreshMapper,
            AnalyticsEtlClient etlClient) {
        this.refreshMapper = refreshMapper;
        this.etlClient = etlClient;
    }

    public Refresh triggerRefresh() {
        EtlRefreshResult result = etlClient.triggerRefresh();
        if ("busy".equalsIgnoreCase(result.status())) {
            throw new ObjectAlreadyExistsException(
                    "An analytics refresh is already in progress");
        }
        if (!"success".equalsIgnoreCase(result.status())) {
            throw new ObjectNotProcessedException(
                    "Analytics refresh failed: " + safeError(result.error()));
        }
        if (result.runId() == null || result.runId().isBlank()) {
            throw new ObjectNotProcessedException("Analytics ETL returned no run ID");
        }
        return getRefreshStatus(result.runId());
    }

    public Refresh getRefreshStatus(String rawRunId) {
        String runId = validateRunId(rawRunId);
        RefreshProjection projection = refreshMapper.findByRunId(runId);
        if (projection == null) {
            throw new ObjectNotFoundException("Analytics refresh not found: " + runId);
        }
        return toRefresh(projection);
    }

    public Refresh getLatestRefresh() {
        RefreshProjection projection = refreshMapper.findLatest();
        if (projection == null) {
            throw new ObjectNotFoundException("No analytics refresh has been recorded");
        }
        return toRefresh(projection);
    }

    private Refresh toRefresh(RefreshProjection projection) {
        return new Refresh(
                projection.etlRunId(),
                projection.status(),
                projection.startedAt(),
                projection.lastEventAt(),
                projection.eventCount(),
                projection.rowCount(),
                projection.errorMessage());
    }

    private String validateRunId(String rawRunId) {
        if (rawRunId == null || !RUN_ID_PATTERN.matcher(rawRunId).matches()) {
            throw new ObjectInvalidException("Invalid analytics refresh run ID");
        }
        return rawRunId;
    }

    private String safeError(String error) {
        return error == null || error.isBlank() ? "no error details" : error;
    }
}