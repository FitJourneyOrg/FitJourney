package dev.rafael.features.profile.data

import dev.rafael.core.database.FitJourneyDatabase

class ProfileLocalDataSource(private val db: FitJourneyDatabase) {
    private val queries = db.profileQueries

    /**
     * Flag de onboarding — só se o cache pertence ao usuário atual. Se o uid guardado for de
     * outro usuário (ou não houver uid), devolve null: assim um cadastro novo NÃO herda o
     * 'true' do usuário anterior no fallback da Splash.
     */
    fun cachedOnboarding(currentUid: String?): Boolean? {
        val row = queries.selectOnboarding().executeAsOneOrNull() ?: return null
        if (currentUid == null || row.uid != currentUid) return null
        return row.onboardingCompleted == 1L
    }

    fun saveOnboarding(uid: String, completed: Boolean) {
        queries.upsertOnboarding(uid = uid, onboardingCompleted = if (completed) 1L else 0L)
    }

    fun clear() {
        queries.clearOnboarding()
    }
}
