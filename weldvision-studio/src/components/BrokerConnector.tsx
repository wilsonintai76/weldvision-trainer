/**
 * BrokerConnector — MQTT broker connection controls.
 */

interface BrokerConnectorProps {
  brokerHost: string;
  connected: boolean;
  onHostChange: (host: string) => void;
  onConnect: () => void;
  onDisconnect: () => void;
}

export function BrokerConnector({
  brokerHost,
  connected,
  onHostChange,
  onConnect,
  onDisconnect,
}: BrokerConnectorProps) {
  return (
    <div>
      <div className="text-xs text-weld-muted mb-1">Broker Address</div>
      <div className="flex gap-1">
        <input
          type="text"
          value={brokerHost}
          onChange={(e) => onHostChange(e.target.value)}
          placeholder="192.168.1.x"
          className="flex-1 bg-weld-bg border border-weld-border rounded px-2 py-1 text-xs text-weld-text placeholder:text-weld-muted focus:outline-none focus:border-weld-accent"
        />
        <button
          onClick={connected ? onDisconnect : onConnect}
          className={`
            rounded px-3 py-1 text-xs font-medium cursor-pointer transition-colors
            ${connected
              ? "bg-weld-red text-white hover:bg-[#c62828]"
              : "bg-weld-green text-white hover:bg-[#2ea043]"
            }
          `}
        >
          {connected ? "Disconnect" : "Connect"}
        </button>
      </div>
    </div>
  );
}
