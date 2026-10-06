/**
 * Network Monitor WebSocket / STOMP Native Client
 * Sử dụng Native WebSocket kết nối trực tiếp endpoint STOMP /ws nội bộ, không CDN.
 */
class NetworkMonitorWs {
    constructor() {
        this.socket = null;
        this.subscriptions = new Map();
        this.connected = false;
        this.wasConnected = false;
        this.reconnectAttempts = 0;
        this.maxReconnectDelay = 10000;
        this.reconnectTimeout = null;
        this.statusListeners = [];
        this.reconnectListeners = [];
        this.subCounter = 1;
        this.heartbeatTimer = null;
    }

    onStatusChange(listener) {
        if (typeof listener === 'function' && !this.statusListeners.includes(listener)) {
            this.statusListeners.push(listener);
            listener(this.connected ? 'connected' : (this.reconnectAttempts > 0 ? 'reconnecting' : 'disconnected'));
        }
    }

    onReconnect(listener) {
        if (typeof listener === 'function' && !this.reconnectListeners.includes(listener)) {
            this.reconnectListeners.push(listener);
        }
    }

    notifyStatus(status) {
        this.statusListeners.forEach(fn => {
            try { fn(status); } catch (e) { console.error(e); }
        });
    }

    connect() {
        if (this.reconnectTimeout) {
            clearTimeout(this.reconnectTimeout);
            this.reconnectTimeout = null;
        }

        if (this.socket && (this.socket.readyState === WebSocket.OPEN || this.socket.readyState === WebSocket.CONNECTING)) {
            return;
        }

        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        const wsUrl = `${protocol}//${window.location.host}/ws`;

        try {
            this.notifyStatus(this.reconnectAttempts > 0 ? 'reconnecting' : 'connecting');
            this.socket = new WebSocket(wsUrl);

            this.socket.onopen = () => {
                const connectFrame = "CONNECT\naccept-version:1.1,1.0\nheart-beat:10000,10000\n\n\0";
                this.socket.send(connectFrame);
            };

            this.socket.onmessage = (event) => {
                this.handleMessage(event.data);
            };

            this.socket.onclose = () => {
                this.connected = false;
                this.stopHeartbeat();
                this.notifyStatus('disconnected');
                this.scheduleReconnect();
            };

            this.socket.onerror = (err) => {
                console.warn("WebSocket error:", err);
                this.stopHeartbeat();
                if (this.socket) {
                    try {
                        this.socket.close();
                    } catch (e) {
                        // ignore
                    }
                }
            };
        } catch (e) {
            console.warn("WebSocket init failed:", e);
            this.stopHeartbeat();
            this.scheduleReconnect();
        }
    }

    startHeartbeat() {
        this.stopHeartbeat();
        this.heartbeatTimer = setInterval(() => {
            if (this.socket && this.socket.readyState === WebSocket.OPEN && this.connected) {
                this.socket.send('\n');
            }
        }, 10000);
    }

    stopHeartbeat() {
        if (this.heartbeatTimer) {
            clearInterval(this.heartbeatTimer);
            this.heartbeatTimer = null;
        }
    }

    scheduleReconnect() {
        // Không tạo nhiều reconnect timer nếu timer đã được lên lịch
        if (this.reconnectTimeout) {
            return;
        }
        this.reconnectAttempts++;
        const delay = Math.min(1000 * Math.pow(1.5, this.reconnectAttempts), this.maxReconnectDelay);
        this.notifyStatus('reconnecting');
        this.reconnectTimeout = setTimeout(() => {
            this.reconnectTimeout = null;
            this.connect();
        }, delay);
    }

    handleMessage(data) {
        if (!data || data === '\n' || data === '\r\n') return; // Heartbeat frame

        const frames = data.split('\0');
        for (const rawFrame of frames) {
            if (!rawFrame.trim()) continue;
            const lines = rawFrame.split('\n');
            const command = lines[0].trim();
            const headers = {};
            let bodyIndex = 1;

            for (let i = 1; i < lines.length; i++) {
                if (lines[i].trim() === '') {
                    bodyIndex = i + 1;
                    break;
                }
                const parts = lines[i].split(':');
                if (parts.length >= 2) {
                    headers[parts[0].trim()] = parts.slice(1).join(':').trim();
                }
            }

            const body = lines.slice(bodyIndex).join('\n');

            if (command === 'CONNECTED') {
                const wasReconnecting = this.wasConnected;
                this.connected = true;
                this.reconnectAttempts = 0;
                this.startHeartbeat();
                this.notifyStatus('connected');

                // Resubscribe active subscriptions cleanly (không duplicate subscription)
                this.subscriptions.forEach((sub, dest) => {
                    this.sendSubscribe(dest, sub.id);
                });

                if (wasReconnecting) {
                    // Tải lại snapshot KPI một lần sau khi reconnect thành công
                    if (typeof window.refreshDashboardKpis === 'function') {
                        window.refreshDashboardKpis();
                    }
                    this.reconnectListeners.forEach(listener => {
                        try { listener(); } catch (e) { console.error('Error in reconnect listener:', e); }
                    });
                }
                this.wasConnected = true;
            } else if (command === 'MESSAGE') {
                const destination = headers['destination'];
                if (destination && this.subscriptions.has(destination)) {
                    const sub = this.subscriptions.get(destination);
                    try {
                        const parsed = body ? JSON.parse(body) : null;
                        sub.callbacks.forEach(cb => cb(parsed, headers));
                    } catch (e) {
                        sub.callbacks.forEach(cb => cb(body, headers));
                    }
                }
            } else if (command === 'ERROR') {
                console.warn("STOMP ERROR received:", headers['message'] || body);
                // Đóng kết nối và bắt đầu reconnect khi nhận STOMP ERROR
                this.connected = false;
                this.stopHeartbeat();
                if (this.socket) {
                    try {
                        this.socket.close();
                    } catch (e) {
                        // ignore
                    }
                }
                this.notifyStatus('disconnected');
                this.scheduleReconnect();
                return;
            }
        }
    }

    sendSubscribe(destination, subId) {
        if (this.socket && this.socket.readyState === WebSocket.OPEN && this.connected) {
            const frame = `SUBSCRIBE\nid:${subId}\ndestination:${destination}\n\n\0`;
            this.socket.send(frame);
        }
    }

    subscribe(destination, callback) {
        if (!this.subscriptions.has(destination)) {
            const subId = 'sub-' + (this.subCounter++);
            this.subscriptions.set(destination, { id: subId, callbacks: [callback] });
            if (this.connected) {
                this.sendSubscribe(destination, subId);
            }
        } else {
            const sub = this.subscriptions.get(destination);
            if (!sub.callbacks.includes(callback)) {
                sub.callbacks.push(callback);
            }
        }
    }
}

// Global singleton instance
window.ndmmsWs = new NetworkMonitorWs();
document.addEventListener('DOMContentLoaded', () => {
    window.ndmmsWs.connect();

    if (document.getElementById('ports-table-body')) {
        window.ndmmsWs.subscribe('/topic/ports', (data) => {
            if (!data || !data.monitorId) return;
            const badge = document.getElementById('port-badge-' + data.monitorId);
            if (badge) {
                badge.textContent = data.currentStatus;
                badge.className = 'badge ' + (data.currentStatus === 'UP' ? 'ONLINE' : (data.currentStatus === 'DOWN' ? 'OFFLINE' : 'UNKNOWN'));
            }
            const checkedCell = document.getElementById('port-checked-' + data.monitorId);
            if (checkedCell && data.timestamp) {
                const date = new Date(data.timestamp);
                checkedCell.textContent = date.toLocaleDateString('vi-VN') + ' ' + date.toLocaleTimeString('vi-VN');
            }
        });
    }
});
