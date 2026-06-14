declare module "undici" {
  export class Agent {
    constructor(opts?: Record<string, unknown>);
  }
  export class ProxyAgent {
    constructor(url: string, opts?: Record<string, unknown>);
  }
  export function setGlobalDispatcher(agent: Agent | ProxyAgent): void;
  export function getGlobalDispatcher(): Agent | ProxyAgent;
}
