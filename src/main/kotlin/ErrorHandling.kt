package org.example

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
        fun findById(jobId: JobId): Job
    }

    class NaiveJobs : Jobs {
        override fun findById(id: JobId): Job {
            return JOBS_DATABASE[id] ?: throw IllegalArgumentException("Job not found")
        }
    }

    class JobsService(private val jobs: Jobs) {
        fun retrieveSalary(jobId: JobId): Double {
            val job = jobs.findById(jobId)
            return try {
                job.salary.value
            } catch (e: Exception) {
                0.0
            }
        }
    }
    // referential transparency: a function is referentially transparent if it can be replaced with its value without changing the program's behavior
    // functions that throw exceptions are NOT RT
    // we want the compiler to warn us of potential errors
    // "checked" exceptions don't work well with FP because higher order functions

    @JvmStatic
    fun main(args: Array<String>) {
        val jobs: Jobs = NaiveJobs()
        val jobsService = JobsService(jobs)
        val jobId = 42
        val salary = jobsService.retrieveSalary(JobId(jobId.toString()))
        println("Salary for job $jobId is $salary")
    }
}