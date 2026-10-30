package jyk.bcar.configuration

import jyk.bcar.automation.job.AssignCarsJob
import jyk.bcar.automation.job.CollectCategoryJob
import jyk.bcar.automation.job.CollectDetailJob
import jyk.bcar.automation.job.CollectDraftJob
import jyk.bcar.automation.job.DetailCollectionOptions
import jyk.bcar.automation.job.SyncUploadJob
import jyk.bcar.automation.job.SyncUploadOptions
import jyk.bcar.automation.job.assign.AssignStrategy
import jyk.bcar.automation.job.assign.RatioAssignStrategy
import jyk.bcar.automation.job.boolOption
import jyk.bcar.automation.job.decider.DefaultJobChainDecider
import jyk.bcar.automation.job.intOption
import jyk.bcar.automation.job.option
import jyk.bcar.policy.AssignPolicy
import jyk.bcar.policy.DetailCollectionPolicy
import jyk.bcar.port.CategoryTreeSource
import jyk.bcar.port.DetailCarSource
import jyk.bcar.port.DraftCarSource
import jyk.bcar.port.TargetCarSite
import jyk.bcar.repository.CarRepository
import jyk.bcar.repository.CategoryTreeRepository
import jyk.bcar.repository.ExcludedAgencyRepository
import jyk.bcar.repository.PipelineControlRepository
import jyk.bcar.repository.UserRepository
import org.springframework.boot.ApplicationArguments
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class JobConfiguration {
    @Bean
    fun assignStrategy(properties: AssignProperties): AssignStrategy =
        RatioAssignStrategy(AssignPolicy(properties.ratio, properties.fallbackOrder))

    @Bean
    fun jobChainDecider(properties: BatchProperties) = DefaultJobChainDecider(properties.detailShards)

    @Bean
    fun collectDraftJob(source: DraftCarSource, cars: CarRepository, control: PipelineControlRepository) =
        CollectDraftJob(source, cars, control)

    @Bean
    fun collectCategoryJob(source: CategoryTreeSource, users: UserRepository, categories: CategoryTreeRepository) =
        CollectCategoryJob(source, users, categories)

    @Bean
    fun assignCarsJob(users: UserRepository, cars: CarRepository, excluded: ExcludedAgencyRepository, strategy: AssignStrategy) =
        AssignCarsJob(users, cars, excluded, strategy)

    @Bean
    fun collectDetailJob(
        cars: CarRepository,
        control: PipelineControlRepository,
        args: ApplicationArguments,
        properties: BatchProperties,
        source: DetailCarSource,
    ): CollectDetailJob {
        val options = if (args.option("job") == "collect-detail") {
            DetailCollectionOptions(
                shards = args.intOption("shards") ?: 1,
                shard = args.intOption("shard") ?: System.getenv("AWS_BATCH_JOB_ARRAY_INDEX")?.toInt() ?: 0,
                hop = args.intOption("hop") ?: 0,
                idle = args.intOption("idle") ?: 0,
            )
        } else {
            DetailCollectionOptions()
        }
        return CollectDetailJob(
            cars,
            control,
            options,
            DetailCollectionPolicy(properties.detailMaxHops, properties.detailMaxIdleHops),
            source,
        )
    }

    @Bean
    fun syncUploadJob(
        site: TargetCarSite,
        users: UserRepository,
        cars: CarRepository,
        categories: CategoryTreeRepository,
        properties: UploadProperties,
        args: ApplicationArguments,
    ): SyncUploadJob {
        val options = if (args.option("job") == "sync-upload") {
            SyncUploadOptions(
                userId = args.option("user"),
                delete = args.boolOption("delete", default = true),
                submit = args.boolOption("submit", default = true),
                limit = args.intOption("limit") ?: Int.MAX_VALUE,
            )
        } else {
            SyncUploadOptions(userId = null)
        }
        return SyncUploadJob(site, users, cars, categories, properties.toPolicy(), options)
    }
}
