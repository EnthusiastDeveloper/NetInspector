# C-12: `dhcpInfo` is deprecated and often wrong

Status: Accepted

See also [ADR-0008](0008-reference-network-baseline.md).

**Symptom** Gateway or netmask incorrect, IPv6-only networks report nothing.

**Mitigation** Read `ConnectivityManager.getLinkProperties(network)`:
`linkAddresses` for address plus prefix length, `routes` filtered on `isDefaultRoute` for
the gateway, `dnsServers` for resolvers. Never assume a /24 prefix - deriving the sweep
range from an assumed /24 both misses hosts on larger subnets and wastes probes on
smaller ones.
