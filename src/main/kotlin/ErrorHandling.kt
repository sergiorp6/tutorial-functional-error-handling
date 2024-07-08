package org.example

import arrow.core.Option
import arrow.core.computations.nullable
import arrow.core.computations.option
import arrow.core.none
import arrow.core.toOption

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
        fun findById(id: JobId): Job?
        fun findByIdOpt(id: JobId): Option<Job>
        fun findAll(): List<Job>
    }

    class LiveJobs : Jobs {
        override fun findById(id: JobId) = try {
            JOBS_DATABASE.getValue(id)
        } catch (e: Exception) {
            null
        }

        override fun findByIdOpt(id: JobId): Option<Job> = try {
            JOBS_DATABASE[id].toOption()
        } catch (e: Exception) {
            none()
        }

        override fun findAll(): List<Job> = JOBS_DATABASE.values.toList()
    }

    class JobsService(private val jobs: Jobs, private val converter: CurrencyConverter) {
        fun retrieveSalary(jobId: JobId): Double {
            return jobs.findById(jobId)?.salary?.value ?: 0.0
        }

        // This function calls CurrencyConverter when job is not null
        fun retrieveSalaryEur(id: JobId): Double =
            jobs.findById(id)?.let {
                converter.usd2Eur(it.salary.value)
            } ?: 0.0

        // Another example with takeIf construct
        fun isFromCompany(id: JobId, company: String): Boolean =
            jobs.findById(id)?.takeIf { it.company.name == company } != null

        // This method shows how nesting lets can create unreadable code. Can be solved with Arrow
        fun sumSalaries(jobId1: JobId, jobId2: JobId): Double? {
            val maybeJob1: Job? = jobs.findById(jobId1)
            val maybeJob2: Job? = jobs.findById(jobId2)
            return maybeJob1?.let { job1 ->
                maybeJob2?.let { job2 ->
                    job1.salary.value + job2.salary.value
                }
            }
        }

        // Same method using arrow. The evaluation of the code will stop as soon as a null value is found
        fun sunSalaries_V2(jobId1: JobId, jobId2: JobId): Double? = nullable.eager {
            println("Searchoing for job $jobId1")
            val job1: Job = jobs.findById(jobId1).bind()
            println("Job 1 found: $job1")
            println("Searching for job $jobId2")
            // you can also use ensureNotNull
            val job2: Job = jobs.findById(jobId2).bind()
            println("Job 2 found: $job2")
            job1.salary.value + job2.salary.value
        }

        fun salaryGapVsMax(jobId: JobId): Option<Double> {
            val maybeJob: Option<Job> = jobs.findByIdOpt(jobId)
            // note how easy is to have this type of construction in a functional way
            val maybeMaxSalary = jobs.findAll().maxBy { it.salary.value }.toOption().map { it.salary }

            // how to improve this nested code?
            return maybeJob.flatMap { job ->
                maybeMaxSalary.map { maxSalary ->
                    maxSalary.value - job.salary.value
                }
            }
        }

        // Using option.eager we can avoid nested code
        fun salaryGapVsMax_V2(jobId: JobId): Option<Double> = option.eager {
            println("Searching for job $jobId")
            val job: Job = jobs.findByIdOpt(jobId).bind()
            println("Job found: $job")
            println("Searching for the max salary job")
            val maxSalaryJob: Job = jobs.findAll().maxBy { it.salary.value }.toOption().bind()
            println("Max salary job found $maxSalaryJob")
            maxSalaryJob.salary.value - job.salary.value
        }
    }


    // This class will show how to handle nullable types
    class CurrencyConverter {
        fun usd2Eur(amount: Double): Double = amount * 0.91
    }


    @JvmStatic
    fun main(args: Array<String>) {
        val jobs: Jobs = LiveJobs()
        val converter = CurrencyConverter()
        val jobsService = JobsService(jobs, converter)
        val jobId = 1
        val isAppleJob = jobsService.isFromCompany(JobId(jobId.toString()), "Apple")
        println("Job is $jobId ${if (isAppleJob) "is" else "is not"} from Apple")
        // In the logs you will see that "Job 2 found" log is not printed because is not executed
        val sumSalaries = jobsService.sunSalaries_V2(JobId("1"), JobId("42")) ?: 0.0
        println("sum of salaries of jobs: $sumSalaries")
        println("Testing option examples...")
        val salaryDiff = jobsService.salaryGapVsMax(JobId("42"))
        println("Salary diff V1: $salaryDiff")
        // In the logs you will see that no logs are printed after "Searching for job 42" because the job is not found
        val salaryDiff_v2 = jobsService.salaryGapVsMax_V2(JobId("42"))
        println("Salary diff V2: $salaryDiff_v2")

    }
}
