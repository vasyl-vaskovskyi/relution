import { platformForKind } from './platform';

describe('platformForKind', () => {
  it('opens Mac apps on Mac', () => {
    expect(platformForKind('MAC_APP')).toBe('mac');
  });

  it('opens iOS apps on iOS', () => {
    expect(platformForKind('IOS_APP')).toBe('ios');
  });

  it('falls back to iOS for other or unknown kinds', () => {
    expect(platformForKind('OTHER')).toBe('ios');
    expect(platformForKind('SOMETHING_NEW')).toBe('ios');
    expect(platformForKind(null)).toBe('ios');
  });
});
