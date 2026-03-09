package com.twinmind.app.data.remote

import kotlinx.coroutines.delay
import java.io.File
import javax.inject.Inject

class MockTranscriptionService @Inject constructor() : TranscriptionService {

    private val mockTranscripts = listOf(
        "Welcome everyone to today's meeting. Let's start by reviewing our progress on the current sprint. " +
                "We've completed most of the user authentication features and the dashboard redesign is about 80% done.",
        "The API integration team has made significant progress. We've successfully connected to the third-party " +
                "payment processor and are now working on error handling and retry logic for failed transactions.",
        "For the next sprint, we need to focus on performance optimization. The app load time has increased " +
                "by 2 seconds over the last month. We should investigate lazy loading and code splitting options.",
        "Action items from this meeting: First, John will complete the dashboard redesign by Friday. " +
                "Second, Sarah will set up performance monitoring dashboards. Third, the team will review the API documentation.",
        "Let's also discuss the upcoming product launch. Marketing needs the final screenshots by next Wednesday. " +
                "QA should start regression testing on Monday. We need to ensure all critical bugs are resolved.",
        "The customer feedback from last release has been positive overall. Users particularly liked the new " +
                "search functionality. However, some users reported issues with the notification system on older devices.",
        "Budget update: We're currently under budget by about 10%. This gives us room to potentially hire " +
                "a contractor for the mobile optimization work we discussed last week.",
        "In terms of technical debt, we have three critical items. The database migration scripts need updating, " +
                "the logging framework should be upgraded, and we need to refactor the authentication module."
    )

    override suspend fun transcribe(audioFile: File): String {
        // Simulate API delay
        delay(1500L + (Math.random() * 1000).toLong())

        val index = audioFile.nameWithoutExtension
            .replace("chunk_", "")
            .substringAfterLast("_")
            .toIntOrNull() ?: 0

        return mockTranscripts[index % mockTranscripts.size]
    }
}
