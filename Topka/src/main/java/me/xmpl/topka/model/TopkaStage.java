package me.xmpl.topka.model;

public enum TopkaStage {
    /**
     * Отсчет времени до начала наполнения сундука (cooldown-seconds: 3600).
     * Если игроков меньше min-online-players — таймер ставится на паузу без сброса и показывает голограмму not-enough-players.
     */
    TIMER,

    /**
     * Наполнение сундука (случайное время от 15 до 20 секунд) с анимацией трех точек на голограмме.
     */
    FILLING,

    /**
     * Сундук заспавнился (предметы внутри), идет отсчет перезарядки (restock-timer-seconds: 900).
     */
    SPAWNED
}