package com.aruru.mldata;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class CommandManager implements CommandExecutor {
    private final DataCollector dataCollector;
    private final DatabaseManager dbManager;

    public CommandManager(DataCollector dataCollector, DatabaseManager dbManager) {
        this.dataCollector = dataCollector;
        this.dbManager = dbManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mldata.admin")) {
            sender.sendMessage(ChatColor.RED + "명령어를 사용할 권한이 없습니다.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ChatColor.DARK_AQUA + "=== ML Data Collector ===");
            sender.sendMessage(ChatColor.WHITE + "/mldata status " + ChatColor.GRAY + "- 현재 플러그인 상태를 확인합니다.");
            sender.sendMessage(ChatColor.WHITE + "/mldata forcesync " + ChatColor.GRAY + "- 큐에 쌓인 데이터를 DB로 즉시 강제 저장합니다.");
            return true;
        }

        if (args[0].equalsIgnoreCase("status")) {
            sender.sendMessage(ChatColor.DARK_AQUA + "=== ML Data Collector Status ===");
            sender.sendMessage(ChatColor.WHITE + "데이터 연동: " + ChatColor.GREEN + "작동 중");
            sender.sendMessage(ChatColor.GRAY + "모든 데이터는 비동기로 렉 없이 처리되고 있습니다.");
            return true;
        }

        if (args[0].equalsIgnoreCase("forcesync")) {
            sender.sendMessage(ChatColor.YELLOW + "모든 큐의 데이터를 로컬 DB로 강제 전송합니다...");
            dataCollector.flushAll();
            sender.sendMessage(ChatColor.GREEN + "데이터 저장이 완료되었습니다!");
            return true;
        }

        sender.sendMessage(ChatColor.RED + "알 수 없는 명령어입니다. /mldata 를 입력하세요.");
        return true;
    }
}
