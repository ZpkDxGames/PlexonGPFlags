package com.plexon.gpflags;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase3PlayerUxArchitectureTest {
    @Test void claimsHomeCoversCurrentClaimPresenceAndAbsence() throws Exception {
        String menu = text("src/main/java/com/plexon/gpflags/gui/MenuService.java");
        assertTrue(menu.contains("Current Claim"));
        assertTrue(menu.contains("You are not standing inside a claim."));
        assertTrue(menu.contains("You are inside a claim you cannot manage."));
        assertTrue(menu.contains("My Claims"));
        assertTrue(menu.contains("Create Claim"));
        assertTrue(menu.contains("Claim Shovel"));
    }

    @Test void claimCardsAndTitlesDoNotRequireRawNumericIdentity() throws Exception {
        String claim = text("src/main/java/com/plexon/gpflags/gui/ClaimPresentation.java");
        String service = text("src/main/java/com/plexon/gpflags/claim/ClaimService.java");
        String config = text("src/main/resources/config.yml");
        assertFalse(claim.contains("Claim #"));
        assertFalse(service.contains("return \"Claim #\""));
        assertFalse(config.contains("#%claim%"));
        assertTrue(claim.contains("Main Claim"));
        assertTrue(claim.contains("Subdivision"));
    }

    @Test void myClaimsPaginationAndBackRestoreExactPage() throws Exception {
        String menu = text("src/main/java/com/plexon/gpflags/gui/MenuService.java");
        String nav = text("src/main/java/com/plexon/gpflags/gui/ClaimNavigationContext.java");
        assertTrue(menu.contains("CONTENT_SLOTS.length"));
        assertTrue(menu.contains("ClaimNavigationContext.claims(holder.page)"));
        assertTrue(menu.contains("openClaims(player, navigation.claimsPage())"));
        assertTrue(nav.contains("claimsPage = Math.max(1, claimsPage)"));
    }

    @Test void dashboardProvidesTheSupportedClaimActions() throws Exception {
        String menu = text("src/main/java/com/plexon/gpflags/gui/MenuService.java");
        for (String marker : new String[]{"<b>Rules</b>", "<b>Trusted Players</b>", "<b>Claim Areas</b>",
                "<b>Resize</b>", "<b>Teleport</b>", "<b>Show Boundary</b>", "<b>Abandon Claim</b>"}) {
            assertTrue(menu.contains(marker), marker);
        }
    }

    @Test void areaNavigationPreservesMainSubdivisionAndOriginContext() throws Exception {
        String menu = text("src/main/java/com/plexon/gpflags/gui/MenuService.java");
        String nav = text("src/main/java/com/plexon/gpflags/gui/ClaimNavigationContext.java");
        assertTrue(menu.contains("Main Claim + "));
        assertTrue(menu.contains("Currently selected area."));
        assertTrue(nav.contains("areasParentClaimId"));
        assertTrue(nav.contains("areasReturnClaimId"));
        assertTrue(nav.contains("dashboardFromAreas"));
    }

    @Test void rulesUseDetailsAndExplicitAllowBlockResetActions() throws Exception {
        String menu = text("src/main/java/com/plexon/gpflags/gui/MenuService.java");
        assertTrue(menu.contains("FLAG_DETAILS"));
        assertTrue(menu.contains("<b>Allow</b>"));
        assertTrue(menu.contains("<b>Block</b>"));
        assertTrue(menu.contains("presentation.resetLabel()"));
        assertFalse(menu.contains("event.isRightClick()"));
        assertFalse(menu.contains("Left click: toggle"));
        assertFalse(menu.contains("Right click: reset/inherit"));
    }

    @Test void rulePresentationExplainsEffectiveOverrideInheritanceAndServerDefault() throws Exception {
        String menu = text("src/main/java/com/plexon/gpflags/gui/MenuService.java");
        String presentation = text("src/main/java/com/plexon/gpflags/gui/FlagPresentation.java");
        assertTrue(menu.contains("Current behavior:"));
        assertTrue(menu.contains("This claim:"));
        assertTrue(presentation.contains("Allowed"));
        assertTrue(presentation.contains("Blocked"));
        assertTrue(presentation.contains("Custom Override"));
        assertTrue(presentation.contains("Inherited from parent"));
        assertTrue(presentation.contains("Server Default"));
        assertTrue(presentation.contains("Use Parent"));
        assertTrue(presentation.contains("Use Server Default"));
    }

    @Test void flagMutationsRevalidateAndRemainFlagServiceOwned() throws Exception {
        String menu = text("src/main/java/com/plexon/gpflags/gui/MenuService.java");
        assertTrue(menu.contains("holder.token.matches(claim)"));
        assertTrue(menu.contains("flags.set(player, claim, flag, value, \"GUI\")"));
        assertFalse(menu.contains("store.set("));
        assertTrue(menu.contains("private boolean submitOnce()"));
    }

    @Test void trustedPlayersUseDetailsChangeAccessAndConfirmedRemoval() throws Exception {
        String menu = text("src/main/java/com/plexon/gpflags/gui/MenuService.java");
        assertTrue(menu.contains("TRUST_DETAILS"));
        assertTrue(menu.contains("Click to review access."));
        assertTrue(menu.contains("<b>Change Access</b>"));
        assertTrue(menu.contains("TRUST_REMOVE_CONFIRM"));
        assertFalse(menu.contains("Click to remove"));
        assertTrue(menu.contains("actions.removeTrust(player, claim, holder.data, holder.aux)"));
    }

    @Test void trustChoicesAreExactlyAccessContainerBuildManage() throws Exception {
        String menu = text("src/main/java/com/plexon/gpflags/gui/MenuService.java");
        assertTrue(menu.contains("ClaimPermission.Access"));
        assertTrue(menu.contains("ClaimPermission.Inventory"));
        assertTrue(menu.contains("ClaimPermission.Build"));
        assertTrue(menu.contains("ClaimPermission.Manage"));
        assertTrue(menu.contains("\"Container\""));
    }

    @Test void trustPromptUsesCentralAuthorityAndCapturedClaimRevalidation() throws Exception {
        String menu = text("src/main/java/com/plexon/gpflags/gui/MenuService.java");
        String prompt = text("src/main/java/com/plexon/gpflags/service/PromptService.java");
        assertTrue(menu.contains("prompts.start(player, \"prompt-player\""));
        assertTrue(menu.contains("revalidatePrompt(actor, claimId, token, navigation)"));
        assertTrue(prompt.contains("One central chat-prompt listener"));
        assertTrue(prompt.contains("Map.of(\"seconds\", Integer.toString(timeout))"));
        assertTrue(prompt.contains("prompt-expired"));
    }

    @Test void createClaimUsesActionServicePreviewAndShowsAffordability() throws Exception {
        String menu = text("src/main/java/com/plexon/gpflags/gui/MenuService.java");
        String actions = text("src/main/java/com/plexon/gpflags/service/ClaimActionService.java");
        assertTrue(menu.contains("actions.previewCreate"));
        assertTrue(menu.contains("actions.maximumAffordableSquareSide"));
        assertTrue(menu.contains("Required: "));
        assertTrue(menu.contains("Available: "));
        assertTrue(menu.contains("Not enough claim blocks."));
        assertTrue(menu.contains("Custom Size"));
        assertTrue(actions.contains("GriefPrevention.instance.dataStore.createClaim"));
    }

    @Test void resizeUsesExistingPreviewAndMutationAuthorityWithConfirmation() throws Exception {
        String menu = text("src/main/java/com/plexon/gpflags/gui/MenuService.java");
        assertTrue(menu.contains("ClaimActionService.ResizePreview preview = actions.preview"));
        assertTrue(menu.contains("Claim resized = actions.resize"));
        assertTrue(menu.contains("Confirm Resize"));
        assertFalse(menu.contains("config_claims_minArea"));
    }

    @Test void teleportVisualizerAndAbandonRemainExplicitBoundedServiceFlows() throws Exception {
        String menu = text("src/main/java/com/plexon/gpflags/gui/MenuService.java");
        String visualizer = text("src/main/java/com/plexon/gpflags/service/VisualizerService.java");
        assertTrue(menu.contains("teleports.request(player, claim)"));
        assertTrue(menu.contains("visualizer.show(player, claim)"));
        assertTrue(menu.contains("Warmup:"));
        assertTrue(menu.contains("ABANDON_CONFIRM"));
        assertTrue(menu.contains("Confirm Abandon"));
        assertFalse(menu.contains("Delete Claim #"));
        assertTrue(visualizer.contains("max-particles" ) || visualizer.contains("visualizerMaxParticles"));
    }

    @Test void staleIdentityIncludesOwnerParentWorldAndBoundsAndPhase2GenerationGuardRemains() throws Exception {
        String presentation = text("src/main/java/com/plexon/gpflags/gui/ClaimPresentation.java");
        String guard = text("src/main/java/com/plexon/gpflags/gui/MenuSessionGuard.java");
        assertTrue(presentation.contains("UUID ownerId"));
        assertTrue(presentation.contains("Long parentId"));
        assertTrue(presentation.contains("UUID worldId"));
        assertTrue(presentation.contains("Objects.equals(ownerId, claim.getOwnerID())"));
        assertTrue(guard.contains("UUID actor, long generation"));
        assertTrue(guard.contains("plugin.configurationGeneration()"));
    }

    @Test void menuAddsNoRepeatingTaskGlobalClaimScanOrFilesystemIo() throws Exception {
        String menu = text("src/main/java/com/plexon/gpflags/gui/MenuService.java");
        assertFalse(menu.contains("runTaskTimer"));
        assertFalse(menu.contains("getClaims()"));
        assertFalse(menu.contains("Files."));
        assertFalse(menu.contains("saveConfig("));
    }

    @Test void protectionSparsePersistenceMigrationSchemaAndApisRemainProtected() throws Exception {
        String protection = text("src/main/java/com/plexon/gpflags/protection/ProtectionListener.java");
        String store = text("src/main/java/com/plexon/gpflags/flag/FlagStore.java");
        String schema = text("src/main/java/com/plexon/gpflags/config/SchemaVersion.java");
        String plugin = text("src/main/java/com/plexon/gpflags/PlexonGPFlags.java");
        assertFalse(protection.contains("Files."));
        assertFalse(protection.contains("flags.yml"));
        assertFalse(protection.contains("MenuService"));
        assertTrue(store.contains("if (value == null)"));
        assertTrue(store.contains("if (values == null || values.isEmpty()) yaml.set(root, null)"));
        assertTrue(store.contains("migration-backups"));
        assertTrue(store.contains("legacy-import.complete"));
        assertTrue(schema.contains("is newer than supported"));
        assertTrue(plugin.contains("PlexonGPFlagsAPI.class"));
        assertTrue(plugin.contains("net.plexon.claimflags.api.PlexonClaimFlagsAPI.class"));
    }

    @Test void phase3WorkflowRunsForExactHeadAndPhase2BasedDraftPr() throws Exception {
        String workflow = text(".github/workflows/build.yml");
        assertTrue(workflow.contains("'phase3/**'"));
        assertTrue(workflow.contains("branches: [main, 'phase2/**']"));
        assertTrue(workflow.contains("java-version: '25'"));
        assertTrue(workflow.contains("gradle clean check javadoc"));
    }

    private static String text(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}
