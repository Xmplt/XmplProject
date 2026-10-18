package me.xmpl.reputation;

import me.xmpl.lib.extension.XmplExtension;
import me.xmpl.reputation.command.DislikeCommand;
import me.xmpl.reputation.command.LikeCommand;
import me.xmpl.reputation.command.RepCommand;
import me.xmpl.reputation.hook.ReputationPlaceholders;
import me.xmpl.reputation.listener.ReputationJoinListener;
import me.xmpl.reputation.service.ReputationService;
import me.xmpl.reputation.storage.ReputationScoreStorage;
import me.xmpl.reputation.storage.VoteCooldownStorage;
import me.xmpl.reputation.storage.VoteRelationStorage;

import java.util.List;

public class ReputationSystemExtension extends XmplExtension {
    private ReputationScoreStorage scoreStorage;
    private VoteRelationStorage voteStorage;
    private VoteCooldownStorage cooldownStorage;
    private ReputationService reputationService;

    @Override
    public void onEnable() {
        // 1. Генерируем все 6 отдельных конфигов в plugins/XmplLib/Configs/ReputationSystem/
        saveDefaultConfig();
        getCustomConfig("placeholders.yml");
        getCustomConfig("databases.yml");
        getCustomConfig("commands/like.yml");
        getCustomConfig("commands/dislike.yml");
        getCustomConfig("commands/rep.yml");

        // 2. Инициализируем 3 отдельные базы данных (очки репутации, связи голосования и кулдауны)
        this.scoreStorage = new ReputationScoreStorage(this);
        this.voteStorage = new VoteRelationStorage(this);
        this.cooldownStorage = new VoteCooldownStorage(this);

        // 3. Инициализируем сервис репутации
        this.reputationService = new ReputationService(this);

        // 4. Регистрируем команды /like, /dislike и /rep
        List<String> likeAliases = getCustomConfig("commands/like.yml").get().getStringList("aliases");
        registerCommand("like", new LikeCommand(this), likeAliases.toArray(new String[0]));

        List<String> dislikeAliases = getCustomConfig("commands/dislike.yml").get().getStringList("aliases");
        registerCommand("dislike", new DislikeCommand(this), dislikeAliases.toArray(new String[0]));

        List<String> repAliases = getCustomConfig("commands/rep.yml").get().getStringList("aliases");
        registerCommand("rep", new RepCommand(this), repAliases.toArray(new String[0]));

        // 5. Регистрируем слушатель входа и плейсхолдеры (%xmpl_reputationsystem_badge%)
        registerListener(new ReputationJoinListener(this));
        registerPlaceholder(new ReputationPlaceholders(this));
    }

    @Override
    public void onDisable() {}

    public ReputationScoreStorage getScoreStorage() { return scoreStorage; }
    public VoteRelationStorage getVoteStorage() { return voteStorage; }
    public VoteCooldownStorage getCooldownStorage() { return cooldownStorage; }
    public ReputationService getReputationService() { return reputationService; }
}