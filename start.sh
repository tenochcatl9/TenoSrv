#!/bin/bash
# start.sh - Lanza playitd, playit y el servidor Minecraft en 3 paneles de tmux
#            El servidor es el proceso principal.

# --- 1. Preparar el entorno ---
sudo mkdir -p /run/playit   # Asegura que exista el socket

# --- 2. Crear la sesión de tmux con 3 paneles ---
echo "Creando sesión tmux con los 3 procesos..."
tmux new-session -d -s minecraft -n "Servidor"

# Panel 0.0: playitd (demonio)
tmux send-keys -t minecraft:0.0 "sudo playitd --socket-path /run/playit/playitd.sock" C-m

# Panel 0.1: playit (cliente) - se divide horizontalmente
tmux split-window -h -t minecraft:0.0
tmux send-keys -t minecraft:0.1 "sudo playit --socket-path /run/playit/playitd.sock" C-m

# Panel 0.2: servidor Minecraft - se divide verticalmente
tmux split-window -v -t minecraft:0.1
tmux send-keys -t minecraft:0.2 "java -Xms8G -Xmx12G -Dcom.mojang.eula.agree=true -jar server.jar --nogui --bonusChest" C-m

# Ajustar el diseño (el panel grande será el servidor)
tmux select-layout -t minecraft main-horizontal

# --- 3. Obtener el PID del proceso Java del servidor ---
# Esperamos un par de segundos a que arranque
sleep 3

# El PID del panel donde está el servidor (el último de la lista)
PANEL_PID=$(tmux list-panes -t minecraft:0 -F "#{pane_pid}" | tail -1)
# Buscamos el proceso java lanzado desde ese panel
JAVA_PID=$(pgrep -P "$PANEL_PID" -f "java.*server.jar" | head -1)
if [ -z "$JAVA_PID" ]; then
    # Si falla, buscamos por nombre directamente
    JAVA_PID=$(pgrep -f "java.*server.jar" | head -1)
fi

if [ -z "$JAVA_PID" ]; then
    echo "⚠️  No se pudo obtener el PID del servidor. La limpieza automática no funcionará."
else
    echo "✅ Servidor Minecraft detectado (PID: $JAVA_PID)"
fi

# --- 4. Adjuntar la sesión (el script queda bloqueado aquí) ---
echo "=== Sesión tmux iniciada. Para salir sin detener: Ctrl+B, D ==="
tmux attach -t minecraft

# --- 5. Al salir de tmux, comprobar si el servidor sigue vivo ---
if [ -n "$JAVA_PID" ] && kill -0 "$JAVA_PID" 2>/dev/null; then
    echo "ℹ️  El servidor sigue corriendo. No se cierran los procesos de playit."
else
    echo "🛑 El servidor se ha detenido. Cerrando playitd y playit..."
    # Matar los procesos de playit
    sudo pkill -f "playitd.*--socket-path"
    sudo pkill -f "playit.*--socket-path"
    echo "✅ Procesos finalizados."
fi

echo "Script terminado."