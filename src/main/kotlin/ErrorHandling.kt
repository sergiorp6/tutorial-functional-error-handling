package org.example

import arrow.core.*

object ErrorHandling {
    data class Job(val id: JobId, val company: Company, val role: Role, val salary: Salary)

    @JvmInline
    value class JobId(val value: String)

    @JvmInline
    value class Company(val name: String)

    @JvmInline
    value class Role(val name: String)

    @JvmInline
    value class Salary(val value: Double) {
        operator fun compareTo(other: Salary): Int = value.compareTo(other.value)
    }

    val JOBS_DATABASE: Map<JobId, Job> = mapOf(
        JobId("1") to Job(JobId("1"), Company("Google"), Role("Software Engineer"), Salary(100000.0)),
        JobId("2") to Job(JobId("2"), Company("Facebook"), Role("Product Manager"), Salary(120000.0)),
        JobId("3") to Job(JobId("3"), Company("Amazon"), Role("Data Scientist"), Salary(110000.0)),
        JobId("4") to Job(JobId("4"), Company("Microsoft"), Role("Software Engineer"), Salary(95000.0)),
        JobId("5") to Job(JobId("5"), Company("Apple"), Role("Product Manager"), Salary(130000.0)),
        JobId("6") to Job(JobId("6"), Company("Netflix"), Role("Data Scientist"), Salary(115000.0)),
    )

    interface Jobs {
        fun findById(id: JobId): Result<Job?>
        fun findAll(): Result<List<Job>>
    }

    class LiveJobs : Jobs {
        override fun findById(id: JobId): Result<Job?> =
            kotlin.runCatching {
                JOBS_DATABASE[id]
            }
        /* old style with try/catch
        try {
            Result.success(JOBS_DATABASE[id])
        } catch (e: Exception) {
            Result.failure(e)
        }
        */


        override fun findAll(): Result<List<Job>> =
            Result.success(JOBS_DATABASE.values.toList())
    }

    class JobService(private val jobs: Jobs, private val currencyConverter: CurrencyConverter) {
        // Example of checking if a result was successful
        fun maybePrintJob(jobId: JobId) {
            val maybeJob: Result<Job?> = jobs.findById(jobId)
            if (maybeJob.isSuccess) {
                maybeJob.getOrNull()?.apply { println("Job found: $this") } ?: println("Job not found")
            } else {
                println("Something went wrong: ${maybeJob.exceptionOrNull()}")
            }
        }

        // Example of combining map and mapCatching. The mapCatching will tranform a potential exception into a Result.Failure
        // The downside of this is that you can kind of lose the control of the exceptions thrown along the way
        fun getSalaryInEur(jobId: JobId): Result<Double> =
            jobs.findById(jobId)
                .map { it?.salary }
                .mapCatching { currencyConverter.convertUsdToEur(it?.value) }

        fun getSalaryGapVsMaxNonIdiomatic(jobId: JobId): Result<Double> = runCatching {
            val maybeJob: Job? = jobs.findById(jobId).getOrThrow()
            val jobSalary = maybeJob?.salary ?: Salary(0.0)
            val jobList = jobs.findAll().getOrThrow()
            val maxSalary = jobList.maxSalary().getOrThrow()
            maxSalary.value - jobSalary.value
        }

        fun getSalaryGapVsMax(jobId: JobId): Result<Double> =
            //We use flatMap because otherwise we will be returning Result<Result<Double>>
            jobs.findById(jobId).flatMap { maybeJob ->
                val salary = maybeJob?.salary ?: Salary(0.0)
                jobs.findAll().flatMap { jobList ->
                    jobList.maxSalary().map { maxSalary ->
                        maxSalary.value - salary.value
                    }
                }
            }

        fun getSalaryGapVsMaxArrow() // to do (33:00)
    }

    class CurrencyConverter {
        fun convertUsdToEur(amount: Double?): Double =
            if (amount != null && amount >= 0.0)
                amount * 0.91
            else
                throw IllegalArgumentException("Amount must be rpesent and positive")
    }

    fun <T> T.toResult(): Result<T> =
        if (this is Throwable) Result.failure(this) else Result.success(this)

    fun List<Job>.maxSalary(): Result<Salary> = runCatching {
        if (this.isEmpty())
            throw NoSuchElementException("No jobs present")
        else
            this.maxBy { it.salary.value }.salary
    }

    // In this tutorial we will deal with potentially FAILED computations
    // Example of successful result
    val appleJobResult: Result<Job> = Result.success(
        Job(JobId("1"), Company("Google"), Role("Software Engineer"), Salary(100000.0)),
    )

    // Example of failed result
    val notFoundJob: Result<Job> = Result.failure(NoSuchElementException("Job not found"))

    // Transforming Results of something on results of something else, provided they are successful
    val appleJobSalary = appleJobResult.map { it.salary }

    // Maps but wraps the potential exception into a Result.Failure
    val appleJobSalaryCatching = appleJobResult.mapCatching { it.salary }


    @JvmStatic
    fun main(args: Array<String>) {
        val jobs = LiveJobs()
        val currencyConverter = CurrencyConverter()
        val jobsService = JobService(jobs, currencyConverter)
        // job found
        jobsService.maybePrintJob(JobId("2"))
        // job not found
        jobsService.maybePrintJob(JobId("42"))
        // example of currency conversion failed
        val maybeSalaryFailed = jobsService.getSalaryInEur(JobId("42"))
        println(maybeSalaryFailed)

        // example of currency conversion successful
        val maybeSalarySuccessful = jobsService.getSalaryInEur(JobId("2"))
        println(maybeSalarySuccessful)

        // example of handling specific exceptions wrapped inside Result.Failure
        val recovered = maybeSalaryFailed.recover {
            when (it) {
                is IllegalArgumentException -> println("Amount must be positive.")
                else -> println("Some other error occurred: ${it.message}")
            }
            0.0 // fallback value
        }
        println(recovered)
        // another way to do it with fold
        val finalStatement = maybeSalaryFailed.fold(
            {
                "The salary of the job is $it"
            },
            {
                when (it) {
                    is IllegalArgumentException -> println("Amount must be positive.")
                    else -> println("Some other error occurred: ${it.message}")
                }
                "Job not found so we have 0.0"
            }
        )
        println(finalStatement)
    }
}
