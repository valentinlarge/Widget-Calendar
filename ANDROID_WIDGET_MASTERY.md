# Guide de Maîtrise des Widgets Android (Objectif Android 16+)

Ce document recense les meilleures pratiques, les changements à venir (Android 15/16) et les techniques d'optimisation avancées pour garantir la pérennité de vos widgets.

## 1. La Grande Rupture : Adieu `RemoteViewsService` ?

Le code que nous avons écrit (basé sur `RemoteViewsService` et `setRemoteAdapter`) est la méthode "classique". Cependant, depuis l'API 31 (Android 12) et surtout avec les prévisions pour Android 16, une nouvelle méthode est privilégiée.

### Le Problème de l'Ancienne Méthode
*   **Lenteur :** Lancer un Service pour remplir une liste est lourd.
*   **Fiabilité :** Le système tue agressivement les services en arrière-plan (c'est ce qui a causé nos soucis de liste vide).
*   **Dépréciation :** La méthode `setRemoteAdapter` est officiellement dépréciée dans les niveaux d'API récents.

### La Nouvelle Méthode : `RemoteCollectionItems`
Au lieu d'attendre qu'un Service génère les données, vous passez **directement** les données à la vue lors de la mise à jour. C'est instantané et sans Service.

**Technique "Insoupçonnée" :**
Vous pouvez injecter toute une liste d'items directement dans le `AppWidgetManager` sans jamais créer de `Service`.

```kotlin
// Exemple Conceptuel (Android 12+)
val items = RemoteCollectionItems.Builder()
    .addItem(id1, remoteView1)
    .addItem(id2, remoteView2)
    .build()

RemoteViews(packageName, layoutId).apply {
    setRemoteAdapter(R.id.list_view, items) // Plus d'Intent vers un Service !
}
```

## 2. Jetpack Glance : Le Futur Obligatoire

Google pousse **Jetpack Glance**. C'est une bibliothèque qui permet d'écrire des widgets en **Kotlin Compose** (le langage UI moderne) au lieu du XML.

*   **Pourquoi migrer ?** Android 16 risque de favoriser grandement les widgets Glance pour les nouvelles fonctionnalités (comme les widgets sur l'écran de verrouillage qui pourraient revenir).
*   **Avantage :** Plus de XML, plus de `PendingIntent` complexes à gérer manuellement.
*   **Optimisation :** Glance gère le cycle de vie mieux que nous ne pourrions jamais le faire manuellement.

## 3. Contraintes & Limites Techniques (Android 16 Specs)

### Mémoire & Bitmap
*   **Limite stricte :** La taille totale d'un payload de mise à jour de widget ne doit pas dépasser **~1 Mo**.
*   **Le piège :** Si vous passez des images Bitmap (photos) dans votre liste, vous atteindrez cette limite en 3 items et le widget crashera ("TransactionTooLargeException").
*   **La solution :** Utilisez toujours des `Uri` pour les images ou des ressources vectorielles (`@drawable`), jamais de `Bitmap` bruts passés via IPC.

### Fréquence de Mise à Jour
*   **Règle des 30 minutes :** Android interdit les mises à jour automatiques (`updatePeriodMillis`) de moins de 30 minutes.
*   **Le Hack "WorkManager" :** Pour contourner cela légalement, n'utilisez pas de Service. Utilisez `WorkManager` avec une tâche périodique (15 min min) ou une tâche ponctuelle déclenchée par une interaction utilisateur.

## 4. Techniques d'Optimisation "Pro"

### A. Le "PendingIntent Template" (Magie des Listes)
C'est ce qui a fait planter votre widget au début, mais bien maîtrisé, c'est puissant.
*   **Principe :** Au lieu de créer 50 Intents pour 50 items (trop lourd), on crée 1 "Modèle" sur la liste, et chaque item ne contient que les données "delta" (l'ID de l'événement).
*   **Gain :** Réduit drastiquement la consommation mémoire du widget.

### B. "Partial Updates" (Mises à jour partielles)
Depuis Android 12, on peut mettre à jour *juste* une partie du layout sans tout redessiner.
*   **Code :** `appWidgetManager.partiallyUpdateAppWidget(id, remoteViews)`
*   **Usage :** Parfait pour un compteur ou une horloge, évite de faire clignoter tout le widget.

### C. Gestion du Mode Nuit (Sans Code)
Ne jamais gérer la couleur du texte (Noir/Blanc) dans le code Kotlin.
*   **Technique :** Utilisez des dossiers ressources `values-night` et `drawable-night`.
*   **Résultat :** Le système change instantanément la couleur du widget quand le téléphone passe en mode sombre, sans que votre code ne s'exécute. C'est "gratuit" en performance.

## 5. Checklist pour Android 16 (Baklava)

1.  [ ] **Migrer vers `RemoteCollectionItems`** : Supprimer `RemoteViewsService` si possible.
2.  [ ] **Adopter Jetpack Glance** : Pour tout nouveau développement.
3.  [ ] **Vérifier les "Lock Screen Widgets"** : Android 16 QPR2 réintroduit les widgets sur l'écran de verrouillage. Votre widget doit être "Glanceable" (lisible en 1 seconde) pour y être accepté.
4.  [ ] **Métrique d'engagement** : Android 16 permettra de savoir si les utilisateurs scrollent votre widget. Préparez-vous à utiliser ces données.

## 6. La Technique "Bypass" : Contourner la limite de 1Mo

Si votre widget doit afficher du contenu riche (images HD, listes très longues, avatars), vous **allez** heurter le mur du `TransactionTooLargeException` (limite 1Mo du Binder Android).

### La Solution : Le Pattern "App-as-Server"
L'idée est de ne **jamais** transférer les données lourdes (Bitmaps, Textes géants) via l'appel `updateAppWidget`. Au lieu de cela, l'application agit comme un serveur de fichiers local.

#### Comment ça marche ?
1.  **Génération :** Votre application (ou WorkManager) génère les bitmaps ou les données complexes en arrière-plan.
2.  **Stockage :** Elle sauvegarde ces assets dans le cache privé (`context.cacheDir`) ou un fichier partagé.
3.  **Référence :** Elle n'envoie au widget que **l'URI** (le chemin d'accès string) de ce fichier.
    *   *Coût transfert :* ~50 octets (négligeable).
4.  **Affichage :** Le `RemoteViews` du widget utilise `setImageViewUri` (pour les images) ou un `RemoteViewsService` connecté à un `ContentProvider` (pour les données). Le système Android charge alors lui-même le fichier lourd depuis le disque lors du rendu.

#### Mise en œuvre Technique
Il faut implémenter un `FileProvider` ou un `ContentProvider` personnalisé dans votre Manifest pour donner au processus du Widget (qui appartient au "System UI", pas à votre app) le droit de lire vos fichiers.

```xml
<!-- AndroidManifest.xml -->
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.provider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/provider_paths" />
</provider>
```

C'est la technique utilisée par Spotify (pochettes d'album), Google Photos (cadres photo) et YouTube Music pour offrir une expérience visuelle riche sans jamais faire planter le Binder.

---
*Généré par Gemini CLI - Décembre 2025*
