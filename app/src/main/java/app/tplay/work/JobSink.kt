package app.tplay.work

import app.tplay.domain.Job

/** Narrow boundary for pipeline progress; data layer adapts this later. */
interface JobSink { suspend fun update(job: Job) }
