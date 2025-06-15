package jyk.bcar.runner

import org.springframework.stereotype.Component

@Component
class DraftCollector : Runner {
    override suspend fun run() {
        print("Collecting Drafts...")
    }
}
