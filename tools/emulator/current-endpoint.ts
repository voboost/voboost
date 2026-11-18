/** Beta1 fixed signed inventory: 52 finite catalog targets, each S details <=8KiB; excludes future catalog growth. */
export const RESIDENT_STATUS_BYTES = 1024 * 1024;
export const RESIDENT_STATUS_ENVELOPE_BYTES = RESIDENT_STATUS_BYTES + 256;

/** Root-owned current accepted S attestation. Integer identities stay exact on JSON boundaries. */
export type CurrentEndpoint = {
  targetPid: string; targetStarttime: string; endpointEpoch: string;
  generationId: string; catalogSha256: string; desiredDocumentGeneration: string;
  acceptedStatus?: { documentGeneration: string; receipt: { sequence: string; daemonPid: string; daemonStarttime: string; bootId: string } };
};
const decimal = (value: unknown): value is string => typeof value === "string" && /^[1-9][0-9]*$/.test(value) && BigInt(value) <= 18446744073709551615n;
const revision = (value: unknown) => typeof value === "string" && /^(0|[1-9][0-9]*)$/.test(value) && BigInt(value) <= 9223372036854775805n;
const digest = (value: unknown) => typeof value === "string" && /^[0-9a-f]{64}$/.test(value);
const closed = (value: any, required: string[], optional: string[] = []) => {
  if (!value || typeof value !== "object" || Array.isArray(value) || required.some(key => !Object.hasOwn(value, key)) || Object.keys(value).some(key => !required.includes(key) && !optional.includes(key))) throw new Error("current endpoint closed shape differs");
};
export function currentEndpointShape(value: any): asserts value is CurrentEndpoint {
  closed(value, ["targetPid", "targetStarttime", "endpointEpoch", "generationId", "catalogSha256", "desiredDocumentGeneration"], ["acceptedStatus"]);
  if (!["targetPid", "targetStarttime", "endpointEpoch"].every(key => decimal(value[key])) || !revision(value.desiredDocumentGeneration) || BigInt(value.targetPid) < 2n || BigInt(value.targetPid) > 2147483647n || !digest(value.generationId) || !digest(value.catalogSha256)) throw new Error("current endpoint typed identity differs");
  if (!Object.hasOwn(value, "acceptedStatus")) return;
  const accepted = value.acceptedStatus;
  closed(accepted, ["documentGeneration", "receipt"]);
  closed(accepted.receipt, ["sequence", "daemonPid", "daemonStarttime", "bootId"]);
  if (!revision(accepted.documentGeneration) || accepted.documentGeneration !== value.desiredDocumentGeneration || !["sequence", "daemonPid", "daemonStarttime"].every(key => decimal(accepted.receipt[key])) || BigInt(accepted.receipt.daemonPid) < 2n || BigInt(accepted.receipt.daemonPid) > 2147483647n || !/^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/.test(accepted.receipt.bootId)) throw new Error("current accepted status identity differs");
}
export type CurrentEndpointAuthority = Omit<CurrentEndpoint, "acceptedStatus"> & { bootId: string; daemonPid: string; daemonStarttime: string };
/** Independent OS process and daemon incarnation must be sampled around a fresh CAN checkpoint. */
export function currentAcceptedEndpoint(value: unknown, authority: CurrentEndpointAuthority): CurrentEndpoint | undefined {
  if (value === undefined) return undefined;
  currentEndpointShape(value);
  if (!value.acceptedStatus) return undefined;
  const receipt = value.acceptedStatus.receipt;
  if (!["targetPid", "targetStarttime", "endpointEpoch", "generationId", "catalogSha256", "desiredDocumentGeneration"].every(key => value[key] === authority[key]) || !["bootId", "daemonPid", "daemonStarttime"].every(key => receipt[key] === authority[key])) return undefined;
  return value;
}
/** No historical ACK or queue row may supply a missing current attestation. */
export function stableCurrentAccepted(before: unknown, after: unknown, authority: CurrentEndpointAuthority): boolean {
  const first = currentAcceptedEndpoint(before, authority), last = currentAcceptedEndpoint(after, authority);
  return !!first && !!last && first.acceptedStatus!.documentGeneration === last.acceptedStatus!.documentGeneration && BigInt(last.acceptedStatus!.receipt.sequence) >= BigInt(first.acceptedStatus!.receipt.sequence);
}
