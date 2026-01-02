package jyk.bcar.automation.job.act.draft

import jyk.bcar.automation.job.act.JobAct

interface DraftAct<T, R> : JobAct<T, R> {
    companion object {
        const val COLLECT_ADMIN_URL = "http://thebestcar.kr/mypage/mycar.html"
    }
}
