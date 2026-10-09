// Design tokens ported from the Figma export (cooktool/src/index.css :root).
export const colors = {
  primary: '#FF6B35',
  primaryDark: '#EC5420',
  primarySoft: '#FFF1EB',
  text: '#191F28',
  sub: '#6B7684',
  hint: '#B0B8C1',
  surface: '#F4F5F7',
  line: '#E5E8EB',
  white: '#FFFFFF',
  info: '#3182F6',
  infoSoft: '#EAF2FE',
  caution: '#F59F00',
  cautionSoft: '#FFF6E0',
  cautionText: '#8B5B00',
  success: '#00A77D',
  successSoft: '#E8F8F3',
  danger: '#F04452',
  dangerSoft: '#FFEEEE',
} as const;

// Custom fonts need one family name per weight on iOS.
export const fonts = {
  regular: 'Pretendard-Regular',
  medium: 'Pretendard-Medium',
  semiBold: 'Pretendard-SemiBold',
  bold: 'Pretendard-Bold',
  extraBold: 'Pretendard-ExtraBold',
} as const;

export type FontWeight = keyof typeof fonts;

export const gutter = 20;

export const titleStyle = {
  fontFamily: fonts.extraBold,
  fontSize: 30,
  lineHeight: 38,
  letterSpacing: -0.8,
  color: colors.text,
} as const;
