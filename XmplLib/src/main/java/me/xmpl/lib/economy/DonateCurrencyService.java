package me.xmpl.lib.economy;

public interface DonateCurrencyService {
    /**
     * Получить текущий баланс игрока (всегда целое число >= 0)
     */
    long getBalance(String playerName);

    /**
     * Проверить, хватает ли игроку Эндер-Рублей
     */
    boolean has(String playerName, long amount);

    /**
     * Начислить Эндер-Рубли игроку и записать транзакцию в историю
     * @param playerName Ник игрока
     * @param amount Сумма (строго > 0)
     * @param source Кто начислил (например "Магазин сайта" или ник админа)
     * @param description Причина/описание для истории (/endrub)
     */
    boolean deposit(String playerName, long amount, String source, String description);

    /**
     * Списать Эндер-Рубли у игрока (например, при покупке в других плагинах) и записать в историю
     * @param playerName Ник игрока
     * @param amount Сумма списания (строго > 0)
     * @param source Название плагина/магазина
     * @param description На что потрачено (отобразится в /endrub)
     * @return true, если хватило средств и списание прошло успешно
     */
    boolean withdraw(String playerName, long amount, String source, String description);

    /**
     * Установить точный баланс игроку
     */
    boolean setBalance(String playerName, long newBalance, String source, String description);
}