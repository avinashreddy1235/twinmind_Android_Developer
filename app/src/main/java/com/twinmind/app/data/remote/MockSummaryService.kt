package com.twinmind.app.data.remote

import com.twinmind.app.domain.model.SummaryResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class MockSummaryService @Inject constructor() : SummaryApiService {

    override fun generateSummary(transcript: String): Flow<SummaryResult> = flow {
        // Simulate streaming - emit progressively more complete summary
        val title = "Sprint Planning & Progress Review Meeting"
        val summary = "The team discussed current sprint progress, including completion of user authentication " +
                "features and 80% progress on dashboard redesign. API integration with payment processor is " +
                "progressing well with focus on error handling. Performance optimization was identified as a " +
                "priority for the next sprint due to 2-second increase in app load time. The upcoming product " +
                "launch timeline was reviewed with key deadlines for marketing assets and QA testing. Customer " +
                "feedback from the last release was generally positive, with search functionality being well " +
                "received. Budget is currently 10% under target, potentially allowing for contractor hiring. " +
                "Three critical technical debt items were identified for resolution."

        val actionItems = listOf(
            "John to complete dashboard redesign by Friday",
            "Sarah to set up performance monitoring dashboards",
            "Team to review API documentation",
            "Marketing to receive final screenshots by next Wednesday",
            "QA to begin regression testing on Monday",
            "Investigate lazy loading and code splitting for performance",
            "Consider hiring contractor for mobile optimization",
            "Update database migration scripts",
            "Upgrade logging framework",
            "Refactor authentication module"
        )

        val keyPoints = listOf(
            "User authentication features completed",
            "Dashboard redesign is 80% complete",
            "Payment processor API integration successful",
            "App load time increased by 2 seconds - needs optimization",
            "Customer feedback positive, especially for search feature",
            "Budget is 10% under target",
            "Three critical technical debt items identified",
            "Product launch preparation on track"
        )

        // Stream title first
        delay(500)
        emit(SummaryResult(title = title))

        // Stream summary in chunks
        val words = summary.split(" ")
        val chunkSize = 8
        for (i in words.indices step chunkSize) {
            delay(200)
            val partialSummary = words.take(minOf(i + chunkSize, words.size)).joinToString(" ")
            emit(SummaryResult(title = title, summary = partialSummary))
        }

        // Stream action items one by one
        for (i in actionItems.indices) {
            delay(300)
            emit(
                SummaryResult(
                    title = title,
                    summary = summary,
                    actionItems = actionItems.take(i + 1)
                )
            )
        }

        // Stream key points one by one
        for (i in keyPoints.indices) {
            delay(250)
            emit(
                SummaryResult(
                    title = title,
                    summary = summary,
                    actionItems = actionItems,
                    keyPoints = keyPoints.take(i + 1)
                )
            )
        }
    }

    override suspend fun generateSummaryBlocking(transcript: String): SummaryResult {
        delay(3000)
        return SummaryResult(
            title = "Sprint Planning & Progress Review Meeting",
            summary = "The team discussed current sprint progress, API integration, performance optimization, " +
                    "and upcoming product launch. Budget is under target and technical debt items were identified.",
            actionItems = listOf(
                "John to complete dashboard redesign by Friday",
                "Sarah to set up performance monitoring dashboards",
                "Team to review API documentation",
                "QA to begin regression testing on Monday",
                "Investigate lazy loading for performance"
            ),
            keyPoints = listOf(
                "User authentication features completed",
                "Dashboard redesign is 80% complete",
                "App load time increased by 2 seconds",
                "Customer feedback positive",
                "Budget is 10% under target"
            )
        )
    }
}
